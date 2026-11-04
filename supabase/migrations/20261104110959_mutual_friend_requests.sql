begin;

create table if not exists huggy_private.friend_requests (
  requester_id uuid not null references huggy_private.profiles(user_id) on delete cascade,
  recipient_id uuid not null references huggy_private.profiles(user_id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (requester_id, recipient_id),
  check (requester_id <> recipient_id)
);
create unique index if not exists huggy_request_pair on huggy_private.friend_requests
  (least(requester_id, recipient_id), greatest(requester_id, recipient_id));
create index if not exists huggy_request_recipient on huggy_private.friend_requests(recipient_id);
alter table huggy_private.friend_requests enable row level security;
revoke all on huggy_private.friend_requests from public, anon, authenticated;

-- A prior one-way follow becomes a request. Independently mutual follows stay friends.
insert into huggy_private.friend_requests(requester_id, recipient_id)
select f.follower_id, f.following_id from huggy_private.follows f
where not exists(select 1 from huggy_private.follows r
  where r.follower_id = f.following_id and r.following_id = f.follower_id)
on conflict do nothing;
delete from huggy_private.follows f where not exists(select 1 from huggy_private.follows r
  where r.follower_id = f.following_id and r.following_id = f.follower_id);

-- Serialize all changes to the same pair, including opposite-direction requests.
create or replace function huggy_private.lock_friend_pair(a uuid, b uuid)
returns void language sql security invoker set search_path = '' as $$
  select pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
    least(a,b)::text || ':' || greatest(a,b)::text, 0));
$$;
revoke all on function huggy_private.lock_friend_pair(uuid,uuid) from public, anon, authenticated;

create or replace function huggy_private.huggy_follow(p_code text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid(); target huggy_private.profiles;
begin
  perform huggy_private.huggy_profile();
  select * into target from huggy_private.profiles where friend_code = lower(trim(p_code));
  if not found then raise exception '친구 코드를 찾지 못했습니다.'; end if;
  if target.user_id = uid then raise exception '내 코드는 등록할 수 없습니다.'; end if;
  perform huggy_private.lock_friend_pair(uid, target.user_id);
  if exists(select 1 from huggy_private.follows where follower_id = uid and following_id = target.user_id)
     and exists(select 1 from huggy_private.follows where follower_id = target.user_id and following_id = uid) then
    return jsonb_build_object('state','friends','nickname',target.nickname);
  end if;
  if exists(select 1 from huggy_private.friend_requests where requester_id = target.user_id and recipient_id = uid) then
    return jsonb_build_object('state','incoming','nickname',target.nickname);
  end if;
  insert into huggy_private.friend_requests(requester_id, recipient_id) values(uid, target.user_id) on conflict do nothing;
  return jsonb_build_object('state','pending','nickname',target.nickname);
end $$;

create or replace function huggy_private.huggy_respond_friend(p_user uuid, p_accept boolean)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'Authentication required'; end if;
  if p_user is null or p_user = uid or p_accept is null then raise exception '잘못된 친구 요청입니다.'; end if;
  perform huggy_private.lock_friend_pair(uid, p_user);
  if not exists(select 1 from huggy_private.friend_requests where requester_id = p_user and recipient_id = uid) then
    if p_accept and exists(select 1 from huggy_private.follows where follower_id = uid and following_id = p_user)
       and exists(select 1 from huggy_private.follows where follower_id = p_user and following_id = uid) then
      return jsonb_build_object('state','friends');
    end if;
    raise exception '이미 처리되었거나 받은 요청이 없습니다.';
  end if;
  if p_accept then
    insert into huggy_private.follows values(uid, p_user), (p_user, uid) on conflict do nothing;
  end if;
  delete from huggy_private.friend_requests where
    (requester_id = p_user and recipient_id = uid) or (requester_id = uid and recipient_id = p_user);
  return jsonb_build_object('state',case when p_accept then 'friends' else 'declined' end);
end $$;

create or replace function huggy_private.huggy_unfollow(p_user uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'Authentication required'; end if;
  if p_user is null or p_user = uid then raise exception '잘못된 친구입니다.'; end if;
  perform huggy_private.lock_friend_pair(uid, p_user);
  delete from huggy_private.follows where (follower_id = uid and following_id = p_user)
    or (follower_id = p_user and following_id = uid);
  delete from huggy_private.friend_requests where (requester_id = uid and recipient_id = p_user)
    or (requester_id = p_user and recipient_id = uid);
end $$;

create or replace function huggy_private.huggy_snapshot()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid(); members jsonb; feed jsonb; requests jsonb;
begin
  perform huggy_private.huggy_profile();
  select coalesce(jsonb_agg(q.data order by q.own desc, q.name), '[]'::jsonb) into members
  from (
    select p.user_id = uid as own, p.nickname as name,
      jsonb_build_object('user_id', p.user_id, 'nickname', p.nickname,
        'own', p.user_id = uid, 'sharing', p.sharing,
        'level', s.level, 'recorded_at', s.recorded_at) as data
    from huggy_private.profiles p
    left join huggy_private.status s on s.user_id = p.user_id and (p.user_id = uid or p.sharing)
    where p.user_id = uid or (exists(select 1 from huggy_private.follows f
      where f.follower_id = uid and f.following_id = p.user_id)
      and exists(select 1 from huggy_private.follows f where f.follower_id = p.user_id and f.following_id = uid))
  ) q;
  select coalesce(jsonb_agg(q.data order by q.time), '[]'::jsonb) into feed from (
    select e.received_at as time,
      jsonb_build_object('event_id', e.event_id, 'nickname', p.nickname,
        'level', e.level, 'recorded_at', e.recorded_at) as data
    from huggy_private.events e join huggy_private.profiles p on p.user_id = e.user_id
    where p.sharing and exists(select 1 from huggy_private.follows f
      where f.follower_id = uid and f.following_id = e.user_id)
      and exists(select 1 from huggy_private.follows f where f.follower_id = e.user_id and f.following_id = uid)
    order by e.received_at desc limit 3
  ) q;
  select coalesce(jsonb_agg(jsonb_build_object('user_id',p.user_id,'nickname',p.nickname,
    'incoming',r.recipient_id = uid,'created_at',r.created_at) order by r.created_at desc), '[]'::jsonb)
  into requests from huggy_private.friend_requests r join huggy_private.profiles p
    on p.user_id = case when r.recipient_id = uid then r.requester_id else r.recipient_id end
    where r.recipient_id = uid or r.requester_id = uid;
  return jsonb_build_object('members',members,'feed',feed,'requests',requests,'friendship_version',2);
end $$;

revoke all on function huggy_private.huggy_respond_friend(uuid,boolean) from public, anon;
grant execute on function huggy_private.huggy_respond_friend(uuid,boolean) to authenticated;
create or replace function public.huggy_respond_friend(p_user uuid, p_accept boolean)
returns jsonb language sql security invoker set search_path = ''
as $$ select huggy_private.huggy_respond_friend(p_user,p_accept); $$;
revoke all on function public.huggy_respond_friend(uuid,boolean) from public, anon;
grant execute on function public.huggy_respond_friend(uuid,boolean) to authenticated;
create or replace function public.huggy_health()
returns jsonb language sql security invoker set search_path = '' as $$ select jsonb_build_object('version',2); $$;
notify pgrst, 'reload schema';
commit;
