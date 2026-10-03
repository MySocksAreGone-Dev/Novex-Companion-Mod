-- Run in a privileged SQL test session. All fixtures and writes are rolled back.
begin;
insert into auth.users(id,email,raw_user_meta_data) values
 ('10000000-0000-4000-8000-000000000001','novex-test-a@example.invalid','{"username":"novex_security_test_a"}'),
 ('10000000-0000-4000-8000-000000000002','novex-test-b@example.invalid','{"username":"novex_security_test_b"}'),
 ('10000000-0000-4000-8000-000000000003','novex-test-c@example.invalid','{"username":"novex_security_test_c"}');
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
do $$ begin
 begin
  insert into public.messages(sender_id,receiver_id,content) values(auth.uid(),'10000000-0000-4000-8000-000000000002','should fail');
  raise exception 'Nonfriend send allowed';exception when insufficient_privilege then null;
 end;
 begin
  insert into public.friends(user_id,friend_id) values(auth.uid(),'10000000-0000-4000-8000-000000000002');
  raise exception 'Direct friendship grant allowed';exception when insufficient_privilege then null;
 end;
 begin
  perform public.companion_record_verified_link(auth.uid(),'20000000-0000-4000-8000-000000000001');
  raise exception 'Forged badge allowed';exception when insufficient_privilege then null;
 end;
end $$;
insert into public.friend_requests(from_id,to_id) values(auth.uid(),'10000000-0000-4000-8000-000000000002');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
select public.accept_friend_request(id) from public.friend_requests where from_id='10000000-0000-4000-8000-000000000001' and to_id=auth.uid();
insert into public.messages(sender_id,receiver_id,content) values(auth.uid(),'10000000-0000-4000-8000-000000000001','authorized test');
select public.companion_presence('20000000-0000-4000-8000-000000000001',true,null,false);
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
do $$ begin
 if (select count(*) from public.companion_unread())<>1 then raise exception 'Unread failed';end if;
 if (select count(*) from public.companion_friends_presence())<>1 then raise exception 'Presence failed';end if;
 if exists(select 1 from public.companion_friends_presence() where server_address is not null or allow_join) then raise exception 'Default server privacy failed';end if;
end $$;
select public.companion_mark_read('10000000-0000-4000-8000-000000000002',now());
do $$ begin if exists(select 1 from public.companion_unread()) then raise exception 'Mark read failed';end if;end $$;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000003',true);
do $$ begin
 if exists(select 1 from public.messages) then raise exception 'Private messages leaked';end if;
 if exists(select 1 from public.companion_social_revision) then raise exception 'Other user revision leaked';end if;
 if exists(select 1 from public.companion_friends_presence()) then raise exception 'Presence leaked';end if;
end $$;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select public.companion_remove_friend('10000000-0000-4000-8000-000000000002');
do $$ begin if exists(select 1 from public.friends) then raise exception 'Remove failed';end if;end $$;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
do $$ begin if exists(select 1 from public.friends) then raise exception 'Reverse remove failed';end if;end $$;
insert into public.friend_requests(from_id,to_id) values(auth.uid(),'10000000-0000-4000-8000-000000000001');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select public.companion_decline_request(id) from public.friend_requests where to_id=auth.uid();
do $$ begin if exists(select 1 from public.friend_requests) then raise exception 'Decline failed';end if;end $$;
rollback;
