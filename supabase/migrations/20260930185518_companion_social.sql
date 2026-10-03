-- Extends existing Novex social tables; preserves launcher RPC and conversations.
begin;
alter policy "messages send" on public.messages with check (
 sender_id=(select auth.uid()) and exists(select 1 from public.friends f where f.user_id=(select auth.uid()) and f.friend_id=receiver_id)
);
alter policy "requests send" on public.friend_requests with check (
 from_id=(select auth.uid()) and to_id<>from_id and status='pending'
 and not exists(select 1 from public.friends f where f.user_id=(select auth.uid()) and f.friend_id=to_id)
);
create table novex_private.companion_reads (
 user_id uuid references public.profiles(id) on delete cascade,
 peer_id uuid references public.profiles(id) on delete cascade,
 read_at timestamptz not null default now(),primary key(user_id,peer_id)
);
create table novex_private.companion_presence (
 user_id uuid primary key references public.profiles(id) on delete cascade,
 session_id uuid not null, expires_at timestamptz not null,
 server_address text, allow_join boolean not null default false
);
alter table novex_private.companion_reads enable row level security;
alter table novex_private.companion_presence enable row level security;
revoke all on novex_private.companion_reads,novex_private.companion_presence from public,anon,authenticated;

create or replace function public.accept_friend_request(request_id uuid)
returns void language plpgsql security definer set search_path='' as $$
declare r public.friend_requests;
begin
 select * into r from public.friend_requests where id=request_id and to_id=auth.uid() and status='pending' for update;
 if not found or r.from_id=r.to_id then raise exception 'Invalid request';end if;
 update public.friend_requests set status='accepted' where id=r.id;
 insert into public.friends(user_id,friend_id) values(r.from_id,r.to_id),(r.to_id,r.from_id) on conflict do nothing;
end $$;
create function public.companion_decline_request(request_id uuid)
returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'Sign in required';end if;
 -- Deleting a declined request permits a future explicit request without forging acceptance.
 delete from public.friend_requests where id=request_id and to_id=auth.uid() and status='pending';
end $$;
create function public.companion_remove_friend(peer_id uuid)
returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'Sign in required';end if;
 delete from public.friends where (user_id=auth.uid() and friend_id=peer_id) or (friend_id=auth.uid() and user_id=peer_id);
 delete from public.friend_requests where (from_id=auth.uid() and to_id=peer_id) or (to_id=auth.uid() and from_id=peer_id);
end $$;
create function public.companion_mark_read(peer_id uuid,seen_at timestamptz)
returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or seen_at is null then raise exception 'Invalid request';end if;
 insert into novex_private.companion_reads(user_id,peer_id,read_at) values(auth.uid(),peer_id,least(seen_at,now()))
 on conflict(user_id,peer_id) do update set read_at=greatest(companion_reads.read_at,excluded.read_at);
end $$;
create function public.companion_unread()
returns table(peer_id uuid,unread bigint) language sql stable security definer set search_path='' as $$
 select m.sender_id,count(*) from public.messages m
 left join novex_private.companion_reads r on r.user_id=auth.uid() and r.peer_id=m.sender_id
 where m.receiver_id=auth.uid() and m.created_at>coalesce(r.read_at,'epoch'::timestamptz)
 group by m.sender_id limit 100;
$$;
create function public.companion_presence(session_id uuid,online boolean,server_address text default null,allow_join boolean default false)
returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or session_id is null then raise exception 'Sign in required';end if;
 if not online then
  delete from novex_private.companion_presence p where p.user_id=auth.uid() and p.session_id=companion_presence.session_id;return;
 end if;
 if server_address is not null and (length(server_address)>253 or server_address !~ '^[A-Za-z0-9][A-Za-z0-9.-]*(:[0-9]{1,5})?$') then raise exception 'Invalid server';end if;
 insert into novex_private.companion_presence as p(user_id,session_id,expires_at,server_address,allow_join)
 values(auth.uid(),session_id,now()+interval '150 seconds',server_address,allow_join and server_address is not null)
 on conflict(user_id) do update set session_id=excluded.session_id,expires_at=excluded.expires_at,server_address=excluded.server_address,allow_join=excluded.allow_join;
end $$;
create function public.companion_friends_presence()
returns table(user_id uuid,server_address text,allow_join boolean) language sql stable security definer set search_path='' as $$
 select p.user_id,p.server_address,p.allow_join from novex_private.companion_presence p
 join public.friends f on f.friend_id=p.user_id and f.user_id=auth.uid()
 where p.expires_at>now() limit 100;
$$;
revoke all on function public.accept_friend_request(uuid), public.companion_decline_request(uuid),public.companion_remove_friend(uuid),public.companion_mark_read(uuid,timestamptz),public.companion_unread(),public.companion_presence(uuid,boolean,text,boolean),public.companion_friends_presence() from public,anon;
grant execute on function public.accept_friend_request(uuid), public.companion_decline_request(uuid),public.companion_remove_friend(uuid),public.companion_mark_read(uuid,timestamptz),public.companion_unread(),public.companion_presence(uuid,boolean,text,boolean),public.companion_friends_presence() to authenticated;
create index if not exists messages_receiver_created_companion on public.messages(receiver_id,created_at);
create index if not exists messages_sender_receiver_created_companion on public.messages(sender_id,receiver_id,created_at);
-- Existing launcher's Realtime content publication is preserved.
alter publication supabase_realtime add table public.messages,public.friend_requests,public.friends;
commit;
