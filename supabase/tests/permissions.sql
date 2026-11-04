set role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', false);
select public.huggy_update_profile('요한', true);
select public.huggy_record(2, 1000, 1000, '10000000-0000-0000-0000-000000000001');
reset role;
-- Save the invite code as a psql variable before testing the authenticated role.
select friend_code as code from huggy_private.profiles
where user_id = '00000000-0000-0000-0000-000000000002' gset
set role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', false);
select public.huggy_profile();
do $$ begin
  if jsonb_array_length(public.huggy_snapshot()->'members') <> 1 then
    raise exception 'Unfollowed profiles leaked'; end if;
  begin
    perform * from huggy_private.status;
    raise exception 'Direct table access was allowed';
  exception when insufficient_privilege then null;
  end;
end $$;
select public.huggy_follow(:'code');
do $$ declare snapshot jsonb; begin
  snapshot := public.huggy_snapshot();
  if jsonb_array_length(snapshot->'members') <> 2 then raise exception 'Follow did not work'; end if;
  if snapshot->'feed'->0->>'level' is distinct from '2' then raise exception 'Shared status missing'; end if;
end $$;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', false);
do $$ begin
  if jsonb_array_length(public.huggy_snapshot()->'feed') <> 0 then raise exception 'Non-follower read events'; end if;
end $$;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', false);
-- Retrying the same event and delivering an older event must not replace the current state.
select public.huggy_record(2, 1000, 1000, '10000000-0000-0000-0000-000000000001');
select public.huggy_record(6, 999, 999, '10000000-0000-0000-0000-000000000002');
reset role;
do $$ begin
  if (select count(*) from huggy_private.events) <> 1 then raise exception 'Duplicate or stale events accepted'; end if;
  if (select level from huggy_private.status) <> 2 then raise exception 'Stale event replaced state'; end if;
end $$;
set role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', false);
select public.huggy_update_profile('요한', false);
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', false);
do $$ declare snapshot jsonb; begin
  snapshot := public.huggy_snapshot();
  if jsonb_array_length(snapshot->'feed') <> 0 then raise exception 'Disabled sharing leaked events'; end if;
  if exists(select 1 from jsonb_array_elements(snapshot->'members') m
    where m->>'user_id' = '00000000-0000-0000-0000-000000000002' and m->'level' <> 'null'::jsonb)
    then raise exception 'Disabled sharing leaked level'; end if;
end $$;
select public.huggy_unfollow('00000000-0000-0000-0000-000000000002');
do $$ begin
  if jsonb_array_length(public.huggy_snapshot()->'members') <> 1 then raise exception 'Unfollow did not work'; end if;
end $$;
reset role;
set role anon;
do $$ begin
  begin
    perform public.huggy_snapshot();
    raise exception 'Anonymous API access was allowed';
  exception when insufficient_privilege then null;
  end;
end $$;
