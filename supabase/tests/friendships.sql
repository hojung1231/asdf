do $$ begin
  if (select count(*) from huggy_private.friend_requests) <> 1 then raise exception 'Legacy request conversion failed'; end if;
  if (select count(*) from huggy_private.follows) <> 2 then raise exception 'Legacy mutual relationships changed'; end if;
end $$;
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',false);
select public.huggy_unfollow('00000000-0000-0000-0000-000000000002');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select public.huggy_update_profile('요한',true);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select public.huggy_update_profile('나',true);
select public.huggy_record(3,2000,2000,'10000000-0000-0000-0000-000000000003');
select public.huggy_follow('0123456789abcdef');
select public.huggy_follow('0123456789abcdef');
do $$ declare s jsonb; begin
  s := public.huggy_snapshot();
  if s->>'friendship_version' <> '2' or jsonb_array_length(s->'members') <> 1
    or jsonb_array_length(s->'feed') <> 0 then raise exception 'Pending request exposed status'; end if;
  if jsonb_array_length(s->'requests') <> 1 or (s->'requests'->0->>'incoming')::boolean then raise exception 'Outgoing request missing or duplicated'; end if;
  begin
    perform public.huggy_respond_friend('00000000-0000-0000-0000-000000000002',true);
    raise exception 'Sender approved their own request';
  exception when raise_exception then
    if SQLERRM = 'Sender approved their own request' then raise; end if;
  end;
  begin
    perform * from huggy_private.friend_requests;
    raise exception 'Direct requests read allowed';
  exception when insufficient_privilege then null; end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',false);
do $$ begin
  if jsonb_array_length(public.huggy_snapshot()->'requests') <> 0 then raise exception 'Third party saw requests'; end if;
  begin
    perform public.huggy_respond_friend('00000000-0000-0000-0000-000000000001',true);
    raise exception 'Third party accepted request';
  exception when raise_exception then
    if SQLERRM = 'Third party accepted request' then raise; end if;
  end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
do $$ declare code text; s jsonb; begin
  s := public.huggy_snapshot();
  if not (s->'requests'->0->>'incoming')::boolean then raise exception 'Incoming request missing'; end if;
end $$;
reset role;
-- Code lookup is test setup only; no user can enumerate profile tables.
select set_config('huggy.test_a_code',(select friend_code from huggy_private.profiles where user_id='00000000-0000-0000-0000-000000000001'),false);
set role authenticated;
do $$ begin
  if public.huggy_follow(current_setting('huggy.test_a_code'))->>'state' <> 'incoming' then raise exception 'Cross request auto-approved'; end if;
  if jsonb_array_length(public.huggy_snapshot()->'members') <> 1 then raise exception 'Cross request leaked status'; end if;
end $$;
select public.huggy_respond_friend('00000000-0000-0000-0000-000000000001',true);
select public.huggy_respond_friend('00000000-0000-0000-0000-000000000001',true);
do $$ declare s jsonb; begin
  s := public.huggy_snapshot();
  if jsonb_array_length(s->'members') <> 2 or jsonb_array_length(s->'requests') <> 0 then raise exception 'Recipient friendship missing'; end if;
  if not exists(select 1 from jsonb_array_elements(s->'members') p where p->>'nickname'='나' and p->>'level'='3') then raise exception 'Recipient cannot see sender status'; end if;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
do $$ declare s jsonb; begin
  s := public.huggy_snapshot();
  if jsonb_array_length(s->'members') <> 2 then raise exception 'Sender friendship missing'; end if;
  if not exists(select 1 from jsonb_array_elements(s->'members') p where p->>'nickname'='요한' and p->>'level'='2') then raise exception 'Sender cannot see recipient status'; end if;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select public.huggy_update_profile('요한',false);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
do $$ declare s jsonb; begin
  s := public.huggy_snapshot();
  if exists(select 1 from jsonb_array_elements(s->'members') p where p->>'nickname'='요한' and p->'level' <> 'null'::jsonb)
    or exists(select 1 from jsonb_array_elements(s->'feed') e where e->>'nickname'='요한') then raise exception 'Friend sharing opt-out leaked state'; end if;
end $$;
select public.huggy_unfollow('00000000-0000-0000-0000-000000000002');
-- Approval retries must not restore a deleted friendship.
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
do $$ begin
  begin
    perform public.huggy_respond_friend('00000000-0000-0000-0000-000000000001',true);
    raise exception 'Deleted friendship was restored';
  exception when raise_exception then if SQLERRM = 'Deleted friendship was restored' then raise; end if; end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select public.huggy_follow('0123456789abcdef');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
do $$ begin
  if jsonb_array_length(public.huggy_snapshot()->'members') <> 1 then raise exception 'Friend removal not mutual'; end if;
end $$;
select public.huggy_respond_friend('00000000-0000-0000-0000-000000000001',false);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
do $$ begin
  if jsonb_array_length(public.huggy_snapshot()->'requests') <> 0 then raise exception 'Decline left request'; end if;
end $$;
select public.huggy_follow('0123456789abcdef');
select public.huggy_unfollow('00000000-0000-0000-0000-000000000002');
do $$ begin
  if jsonb_array_length(public.huggy_snapshot()->'requests') <> 0 then raise exception 'Cancel left request'; end if;
end $$;
select set_config('request.jwt.claim.sub','',false);
do $$ begin
  begin
    perform public.huggy_respond_friend('00000000-0000-0000-0000-000000000001',true);
    raise exception 'Missing identity accepted';
  exception when raise_exception then if SQLERRM = 'Missing identity accepted' then raise; end if; end;
end $$;
reset role;
set role anon;
do $$ begin
  begin
    perform public.huggy_respond_friend('00000000-0000-0000-0000-000000000001',true);
    raise exception 'Anonymous approval allowed';
  exception when insufficient_privilege then null; end;
end $$;
reset role;
