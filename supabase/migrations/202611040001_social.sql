begin;
-- Shared data is limited to nickname, friend code, current level and recent status events.
create schema if not exists huggy_private;
revoke all on schema huggy_private from public, anon, authenticated;

create table if not exists huggy_private.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  nickname text not null check (char_length(nickname) between 1 and 24),
  friend_code text not null unique default left(replace(gen_random_uuid()::text, '-', ''), 16),
  sharing boolean not null default false
);
create table if not exists huggy_private.follows (
  follower_id uuid not null references huggy_private.profiles(user_id) on delete cascade,
  following_id uuid not null references huggy_private.profiles(user_id) on delete cascade,
  primary key (follower_id, following_id),
  check (follower_id <> following_id)
);
create table if not exists huggy_private.status (
  user_id uuid primary key references huggy_private.profiles(user_id) on delete cascade,
  level integer not null check (level between 1 and 6),
  recorded_at bigint not null check (recorded_at > 0),
  changed_at bigint not null,
  event_id uuid not null,
  received_at timestamptz not null default now()
);
create table if not exists huggy_private.events (
  event_id uuid primary key,
  user_id uuid not null references huggy_private.profiles(user_id) on delete cascade,
  level integer not null check (level between 1 and 6),
  recorded_at bigint not null,
  received_at timestamptz not null default now()
);
create index if not exists huggy_events_time on huggy_private.events(received_at desc);
alter table huggy_private.profiles enable row level security;
alter table huggy_private.follows enable row level security;
alter table huggy_private.status enable row level security;
alter table huggy_private.events enable row level security;

create or replace function huggy_private.huggy_profile()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid(); result jsonb;
begin
  if uid is null then raise exception 'Authentication required'; end if;
  insert into huggy_private.profiles(user_id, nickname)
  values(uid, '친구 ' || left(uid::text, 4)) on conflict do nothing;
  select to_jsonb(p) into result from huggy_private.profiles p where user_id = uid;
  return result;
end $$;

create or replace function huggy_private.huggy_update_profile(p_name text, p_sharing boolean)
returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  perform huggy_private.huggy_profile();
  update huggy_private.profiles set nickname = trim(p_name), sharing = p_sharing
  where user_id = auth.uid();
  return huggy_private.huggy_profile();
end $$;

create or replace function huggy_private.huggy_follow(p_code text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare target huggy_private.profiles;
begin
  perform huggy_private.huggy_profile();
  select * into target from huggy_private.profiles where friend_code = lower(trim(p_code));
  if not found then raise exception 'Friend code not found'; end if;
  if target.user_id = auth.uid() then raise exception 'Cannot follow yourself'; end if;
  insert into huggy_private.follows values(auth.uid(), target.user_id) on conflict do nothing;
  return jsonb_build_object('nickname', target.nickname);
end $$;

create or replace function huggy_private.huggy_unfollow(p_user uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  delete from huggy_private.follows where follower_id = auth.uid() and following_id = p_user;
end $$;

create or replace function huggy_private.huggy_record(p_level integer, p_recorded_at bigint, p_changed_at bigint, p_event uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare affected integer; uid uuid := auth.uid();
begin
  perform huggy_private.huggy_profile();
  if not (select sharing from huggy_private.profiles where user_id = uid) then return; end if;
  if p_changed_at > extract(epoch from now()) * 1000 + 300000
     or p_recorded_at > extract(epoch from now()) * 1000 + 300000 then
    raise exception 'Device clock is ahead';
  end if;
  insert into huggy_private.status(user_id, level, recorded_at, changed_at, event_id)
  values(uid, p_level, p_recorded_at, p_changed_at, p_event)
  on conflict(user_id) do update set level = excluded.level, recorded_at = excluded.recorded_at,
    changed_at = excluded.changed_at, event_id = excluded.event_id, received_at = now()
  where (huggy_private.status.changed_at, huggy_private.status.event_id)
        < (excluded.changed_at, excluded.event_id);
  get diagnostics affected = row_count;
  if affected > 0 then
    insert into huggy_private.events values(p_event, uid, p_level, p_recorded_at, now())
    on conflict do nothing;
  end if;
end $$;

create or replace function huggy_private.huggy_snapshot()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid(); members jsonb; feed jsonb;
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
    where p.user_id = uid or exists(
      select 1 from huggy_private.follows f where f.follower_id = uid and f.following_id = p.user_id)
  ) q;
  select coalesce(jsonb_agg(q.data order by q.time), '[]'::jsonb) into feed from (
    select e.received_at as time,
      jsonb_build_object('event_id', e.event_id, 'nickname', p.nickname,
        'level', e.level, 'recorded_at', e.recorded_at) as data
    from huggy_private.events e join huggy_private.profiles p on p.user_id = e.user_id
    where p.sharing and exists(select 1 from huggy_private.follows f
      where f.follower_id = uid and f.following_id = e.user_id)
    order by e.received_at desc limit 3
  ) q;
  return jsonb_build_object('members', members, 'feed', feed);
end $$;

revoke all on function huggy_private.huggy_profile() from public, anon;
revoke all on function huggy_private.huggy_update_profile(text, boolean) from public, anon;
revoke all on function huggy_private.huggy_follow(text) from public, anon;
revoke all on function huggy_private.huggy_unfollow(uuid) from public, anon;
revoke all on function huggy_private.huggy_record(integer, bigint, bigint, uuid) from public, anon;
revoke all on function huggy_private.huggy_snapshot() from public, anon;
grant execute on function huggy_private.huggy_profile() to authenticated;
grant execute on function huggy_private.huggy_update_profile(text, boolean) to authenticated;
grant execute on function huggy_private.huggy_follow(text) to authenticated;
grant execute on function huggy_private.huggy_unfollow(uuid) to authenticated;
grant execute on function huggy_private.huggy_record(integer, bigint, bigint, uuid) to authenticated;
grant execute on function huggy_private.huggy_snapshot() to authenticated;

grant usage on schema huggy_private to authenticated;
create or replace function public.huggy_profile()
returns jsonb language sql security invoker set search_path = ''
as $$ select huggy_private.huggy_profile(); $$;
create or replace function public.huggy_update_profile(p_name text, p_sharing boolean)
returns jsonb language sql security invoker set search_path = ''
as $$ select huggy_private.huggy_update_profile(p_name, p_sharing); $$;
create or replace function public.huggy_follow(p_code text)
returns jsonb language sql security invoker set search_path = ''
as $$ select huggy_private.huggy_follow(p_code); $$;
create or replace function public.huggy_unfollow(p_user uuid)
returns void language sql security invoker set search_path = ''
as $$ select huggy_private.huggy_unfollow(p_user); $$;
create or replace function public.huggy_record(p_level integer, p_recorded_at bigint, p_changed_at bigint, p_event uuid)
returns void language sql security invoker set search_path = ''
as $$ select huggy_private.huggy_record(p_level, p_recorded_at, p_changed_at, p_event); $$;
create or replace function public.huggy_snapshot()
returns jsonb language sql security invoker set search_path = ''
as $$ select huggy_private.huggy_snapshot(); $$;
revoke all on function public.huggy_profile() from public, anon;
revoke all on function public.huggy_update_profile(text, boolean) from public, anon;
revoke all on function public.huggy_follow(text) from public, anon;
revoke all on function public.huggy_unfollow(uuid) from public, anon;
revoke all on function public.huggy_record(integer, bigint, bigint, uuid) from public, anon;
revoke all on function public.huggy_snapshot() from public, anon;
grant execute on function public.huggy_profile() to authenticated;
grant execute on function public.huggy_update_profile(text, boolean) to authenticated;
grant execute on function public.huggy_follow(text) to authenticated;
grant execute on function public.huggy_unfollow(uuid) to authenticated;
grant execute on function public.huggy_record(integer, bigint, bigint, uuid) to authenticated;
grant execute on function public.huggy_snapshot() to authenticated;
create or replace function public.huggy_health()
returns jsonb language sql security invoker set search_path = ''
as $$ select jsonb_build_object('version', 1); $$;
revoke all on function public.huggy_health() from public;
grant execute on function public.huggy_health() to anon, authenticated;
create index if not exists huggy_follows_target on huggy_private.follows(following_id);
create index if not exists huggy_events_user_time on huggy_private.events(user_id, received_at desc);
notify pgrst, 'reload schema';

commit;
