begin;
-- Publish only an owner-readable invalidation counter, never private social rows.
create table public.companion_social_revision (
 user_id uuid primary key references public.profiles(id) on delete cascade,
 revision bigint not null default 1
);
alter table public.companion_social_revision enable row level security;
revoke all on public.companion_social_revision from anon,authenticated;
grant select on public.companion_social_revision to authenticated;
create policy "read own social revision" on public.companion_social_revision for select to authenticated using(user_id=(select auth.uid()));
create function novex_private.touch_companion_social()
returns trigger language plpgsql security definer set search_path='' as $$
declare ids uuid[];
begin
 if tg_table_name='messages' then
  if tg_op='DELETE' then ids=array[old.sender_id,old.receiver_id];else ids=array[new.sender_id,new.receiver_id];end if;
 elsif tg_table_name='friend_requests' then
  if tg_op='DELETE' then ids=array[old.from_id,old.to_id];else ids=array[new.from_id,new.to_id];end if;
 else
  if tg_op='DELETE' then ids=array[old.user_id,old.friend_id];else ids=array[new.user_id,new.friend_id];end if;
 end if;
 insert into public.companion_social_revision as r(user_id,revision)
 select distinct p.id,1 from public.profiles p where p.id=any(ids)
 on conflict(user_id) do update set revision=r.revision+1;
 return null;
end $$;
revoke all on function novex_private.touch_companion_social() from public,anon,authenticated;
create trigger companion_messages_changed after insert or update or delete on public.messages for each row execute function novex_private.touch_companion_social();
create trigger companion_requests_changed after insert or update or delete on public.friend_requests for each row execute function novex_private.touch_companion_social();
create trigger companion_friends_changed after insert or update or delete on public.friends for each row execute function novex_private.touch_companion_social();
alter publication supabase_realtime drop table public.messages,public.friend_requests,public.friends;
alter publication supabase_realtime add table public.companion_social_revision;
commit;
