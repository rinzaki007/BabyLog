-- BabyLog family sync schema for Supabase.
-- Run in Supabase SQL Editor. Never put the service_role key in the Android app.

create extension if not exists pgcrypto;

create table if not exists public.families (
    id uuid primary key default gen_random_uuid(),
    name text not null default '我的家庭',
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now()
);

create table if not exists public.family_members (
    family_id uuid not null references public.families(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'parent' check (role in ('owner', 'parent')),
    joined_at timestamptz not null default now(),
    primary key (family_id, user_id)
);

create table if not exists public.family_invites (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null references public.families(id) on delete cascade,
    invited_email text not null,
    token_hash text not null unique,
    created_by uuid not null references auth.users(id),
    expires_at timestamptz not null,
    accepted_at timestamptz,
    created_at timestamptz not null default now()
);

create table if not exists public.babies (
    id uuid primary key,
    family_id uuid not null references public.families(id) on delete cascade,
    display_name text not null,
    birth_time timestamptz not null,
    sex text,
    avatar_path text,
    birth_weight_grams integer,
    birth_length_cm numeric(5,1),
    birth_head_circumference_cm numeric(5,1),
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create table if not exists public.baby_events (
    id uuid primary key,
    family_id uuid not null references public.families(id) on delete cascade,
    baby_id uuid not null references public.babies(id) on delete cascade,
    event_type text not null,
    occurred_at timestamptz not null,
    payload jsonb not null default '{}'::jsonb,
    created_by uuid references auth.users(id),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    version bigint not null default 1
);
create index if not exists baby_events_family_baby_time_idx
    on public.baby_events(family_id, baby_id, occurred_at desc);

create table if not exists public.baby_photos (
    id uuid primary key,
    family_id uuid not null references public.families(id) on delete cascade,
    baby_id uuid not null references public.babies(id) on delete cascade,
    storage_path text not null,
    caption text,
    taken_at timestamptz not null,
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    version bigint not null default 1
);
create index if not exists baby_photos_family_baby_time_idx
    on public.baby_photos(family_id, baby_id, taken_at desc);

create or replace function public.is_family_member(target_family_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.family_members
        where family_id = target_family_id and user_id = auth.uid()
    );
$$;

create or replace function public.create_family(family_name text default '我的家庭')
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare new_family_id uuid;
begin
    if auth.uid() is null then raise exception 'authentication required'; end if;
    insert into public.families(name, created_by)
    values (coalesce(nullif(trim(family_name), ''), '我的家庭'), auth.uid())
    returning id into new_family_id;
    insert into public.family_members(family_id, user_id, role)
    values (new_family_id, auth.uid(), 'owner');
    return new_family_id;
end;
$$;

create or replace function public.create_family_invite(invited_email text, validity_hours integer default 72)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare invite_token text;
declare target_family_id uuid;
begin
    if auth.uid() is null then raise exception 'authentication required'; end if;
    if validity_hours < 1 or validity_hours > 168 then raise exception 'invalid invite validity'; end if;
    select family_id into target_family_id
      from public.family_members
      where user_id = auth.uid() and role = 'owner'
      order by joined_at limit 1;
    if target_family_id is null then raise exception 'family owner membership required'; end if;
    invite_token := encode(gen_random_bytes(24), 'hex');
    insert into public.family_invites(family_id, invited_email, token_hash, created_by, expires_at)
    values (target_family_id, lower(trim(invited_email)), encode(digest(invite_token, 'sha256'), 'hex'),
            auth.uid(), now() + make_interval(hours => validity_hours));
    return invite_token;
end;
$$;

create or replace function public.accept_family_invite(invite_token text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare invite_row public.family_invites%rowtype;
begin
    if auth.uid() is null then raise exception 'authentication required'; end if;
    select * into invite_row
      from public.family_invites
      where token_hash = encode(digest(invite_token, 'sha256'), 'hex')
        and accepted_at is null and expires_at > now()
      for update;
    if invite_row.id is null then raise exception 'invite is invalid or expired'; end if;
    if lower(invite_row.invited_email) <> lower(coalesce(auth.jwt() ->> 'email', '')) then
        raise exception 'invite email does not match signed-in account';
    end if;
    insert into public.family_members(family_id, user_id, role)
    values (invite_row.family_id, auth.uid(), 'parent')
    on conflict (family_id, user_id) do nothing;
    update public.family_invites set accepted_at = now() where id = invite_row.id;
    return invite_row.family_id;
end;
$$;

alter table public.families enable row level security;
alter table public.family_members enable row level security;
alter table public.family_invites enable row level security;
alter table public.babies enable row level security;
alter table public.baby_events enable row level security;
alter table public.baby_photos enable row level security;

create policy "family members can view family" on public.families
for select to authenticated using (public.is_family_member(id));
create policy "members can view memberships" on public.family_members
for select to authenticated using (public.is_family_member(family_id));
create policy "owners can view invites" on public.family_invites
for select to authenticated using (
    exists (select 1 from public.family_members m
            where m.family_id = family_invites.family_id
              and m.user_id = auth.uid() and m.role = 'owner')
);
create policy "members can read babies" on public.babies
for select to authenticated using (public.is_family_member(family_id));
create policy "members can insert babies" on public.babies
for insert to authenticated with check (public.is_family_member(family_id));
create policy "members can update babies" on public.babies
for update to authenticated using (public.is_family_member(family_id))
with check (public.is_family_member(family_id));
create policy "members can read events" on public.baby_events
for select to authenticated using (public.is_family_member(family_id));
create policy "members can insert events" on public.baby_events
for insert to authenticated with check (public.is_family_member(family_id));
create policy "members can update events" on public.baby_events
for update to authenticated using (public.is_family_member(family_id))
with check (public.is_family_member(family_id));
create policy "members can read photos" on public.baby_photos
for select to authenticated using (public.is_family_member(family_id));
create policy "members can insert photos" on public.baby_photos
for insert to authenticated with check (public.is_family_member(family_id));
create policy "members can update photos" on public.baby_photos
for update to authenticated using (public.is_family_member(family_id))
with check (public.is_family_member(family_id));

revoke all on function public.is_family_member(uuid) from public;
grant execute on function public.is_family_member(uuid) to authenticated;
revoke all on function public.create_family(text) from public;
grant execute on function public.create_family(text) to authenticated;
revoke all on function public.create_family_invite(text, integer) from public;
grant execute on function public.create_family_invite(text, integer) to authenticated;
revoke all on function public.accept_family_invite(text) from public;
grant execute on function public.accept_family_invite(text) to authenticated;

grant select on public.families, public.family_members, public.family_invites to authenticated;
grant select, insert, update on public.babies, public.baby_events, public.baby_photos to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('baby-album', 'baby-album', false, 15728640, array['image/jpeg','image/png','image/webp','image/heic'])
on conflict (id) do nothing;

create policy "family members can read baby album objects" on storage.objects
for select to authenticated using (
    bucket_id = 'baby-album'
    and public.is_family_member((storage.foldername(name))[1]::uuid)
);
create policy "family members can upload baby album objects" on storage.objects
for insert to authenticated with check (
    bucket_id = 'baby-album'
    and public.is_family_member((storage.foldername(name))[1]::uuid)
);
create policy "family members can update baby album objects" on storage.objects
for update to authenticated using (
    bucket_id = 'baby-album'
    and public.is_family_member((storage.foldername(name))[1]::uuid)
) with check (
    bucket_id = 'baby-album'
    and public.is_family_member((storage.foldername(name))[1]::uuid)
);
create policy "family members can delete baby album objects" on storage.objects
for delete to authenticated using (
    bucket_id = 'baby-album'
    and public.is_family_member((storage.foldername(name))[1]::uuid)
);
