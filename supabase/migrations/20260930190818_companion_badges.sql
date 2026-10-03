begin;
create table novex_private.minecraft_links(user_id uuid primary key references public.profiles(id) on delete cascade,minecraft_id uuid unique not null,verified_at timestamptz not null default now());
alter table novex_private.minecraft_links enable row level security;
revoke all on novex_private.minecraft_links from public,anon,authenticated;
create function public.companion_record_verified_link(account_id uuid,minecraft_id uuid)
returns void language plpgsql security definer set search_path='' as $$
begin
 if account_id is null or minecraft_id is null then raise exception 'Invalid identity';end if;
 delete from novex_private.minecraft_links l where l.user_id=account_id;
 insert into novex_private.minecraft_links(user_id,minecraft_id) values(account_id,minecraft_id)
 on conflict on constraint minecraft_links_minecraft_id_key do update set user_id=excluded.user_id,verified_at=now();
end $$;
revoke all on function public.companion_record_verified_link(uuid,uuid) from public,anon,authenticated;
grant execute on function public.companion_record_verified_link(uuid,uuid) to service_role;
create function public.companion_badges(player_ids uuid[])
returns table(minecraft_id uuid) language sql stable security definer set search_path='' as $$
 select l.minecraft_id from novex_private.minecraft_links l where cardinality(player_ids)<=100 and l.minecraft_id=any(player_ids) and l.verified_at>now()-interval '30 days';
$$;
revoke all on function public.companion_badges(uuid[]) from public;
grant execute on function public.companion_badges(uuid[]) to anon,authenticated;
create function public.companion_unlink_minecraft()
returns void language sql security definer set search_path='' as $$ delete from novex_private.minecraft_links where user_id=auth.uid(); $$;
revoke all on function public.companion_unlink_minecraft() from public,anon;
grant execute on function public.companion_unlink_minecraft() to authenticated;
-- Trigger-only function does not need REST execution privileges.
revoke execute on function public.handle_new_user() from public,anon,authenticated;
commit;
