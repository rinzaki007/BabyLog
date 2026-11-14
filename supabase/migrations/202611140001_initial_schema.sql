-- BabyLog initial schema.
-- Every row is scoped to a family; RLS is enabled before client access.

create extension if not exists pgcrypto;

create table if not exists public.families (
    id uuid primary key default gen_random_uuid(),
    name text not null default '我的家庭',
    created_at timestamptz not null default now()
);

create table if not exists public.family_members (
    family_id uuid not null references public.families(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'parent' check (role in ('parent', 'caregiver')),
    joined_at timestamptz not null default now(),
    primary key (family_id, user_id)
);

create table if not exists public.babies (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null references public.families(id) on delete cascade,
    display_name text not null,
    birth_time timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by uuid references auth.users(id)
);

create table if not exists public.baby_events (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null references public.families(id) on delete cascade,
    baby_id uuid not null references public.babies(id) on delete cascade,
    event_type text not null check (event_type in ('feeding', 'diaper', 'sleep', 'measurement', 'health', 'note')),
    occurred_at timestamptz not null,
    payload jsonb not null default '{}'::jsonb,
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1,
    deleted_at timestamptz
);

create index if not exists babies_family_idx on public.babies(family_id);
create index if not exists baby_events_baby_time_idx on public.baby_events(baby_id, occurred_at desc);
create index if not exists baby_events_family_idx on public.baby_events(family_id);

create or replace function public.is_family_member(target_family_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.family_members fm
        where fm.family_id = target_family_id
          and fm.user_id = (select auth.uid())
    );
$$;

revoke all on function public.is_family_member(uuid) from public;
grant execute on function public.is_family_member(uuid) to authenticated;

alter table public.families enable row level security;
alter table public.family_members enable row level security;
alter table public.babies enable row level security;
alter table public.baby_events enable row level security;

create policy "family members can read their families"
on public.families for select to authenticated
using (public.is_family_member(id));

create policy "members can read family membership"
on public.family_members for select to authenticated
using (public.is_family_member(family_id));

create policy "members can read babies"
on public.babies for select to authenticated
using (public.is_family_member(family_id));

create policy "members can create babies"
on public.babies for insert to authenticated
with check (public.is_family_member(family_id) and created_by = (select auth.uid()));

create policy "members can update babies"
on public.babies for update to authenticated
using (public.is_family_member(family_id))
with check (public.is_family_member(family_id));

create policy "members can read baby events"
on public.baby_events for select to authenticated
using (public.is_family_member(family_id));

create policy "members can create baby events"
on public.baby_events for insert to authenticated
with check (
    public.is_family_member(family_id)
    and created_by = (select auth.uid())
    and exists (
        select 1 from public.babies b
        where b.id = baby_id and b.family_id = family_id
    )
);

create policy "members can update baby events"
on public.baby_events for update to authenticated
using (public.is_family_member(family_id))
with check (public.is_family_member(family_id));

grant select, insert, update on public.families to authenticated;
grant select, insert, update on public.family_members to authenticated;
grant select, insert, update on public.babies to authenticated;
grant select, insert, update on public.baby_events to authenticated;
