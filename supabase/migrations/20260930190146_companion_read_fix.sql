create or replace function public.companion_mark_read(peer_id uuid,seen_at timestamptz)
returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or seen_at is null then raise exception 'Invalid request';end if;
 insert into novex_private.companion_reads(user_id,peer_id,read_at) values(auth.uid(),peer_id,least(seen_at,now()))
 on conflict on constraint companion_reads_pkey do update set read_at=greatest(companion_reads.read_at,excluded.read_at);
end $$;
