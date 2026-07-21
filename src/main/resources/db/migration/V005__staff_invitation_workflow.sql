alter type public.app_role add value if not exists 'manager';

create table public.staff_profiles (
  id uuid primary key default gen_random_uuid(),
  auth_user_id uuid unique,
  full_name text not null,
  email text not null,
  phone text,
  avatar_path text,
  job_title text,
  department text,
  employment_type text,
  start_date date,
  primary_role public.app_role not null,
  cv_path text,
  portfolio_url text,
  professional_summary text,
  certificates jsonb not null default '[]'::jsonb,
  internal_notes text,
  status text not null default 'DRAFT'
    check (status in ('DRAFT','INVITED','ACTIVE','SUSPENDED','OFFBOARDED')),
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  activated_at timestamptz
);

create unique index uq_staff_profiles_email on public.staff_profiles (lower(email));
create index idx_staff_profiles_status on public.staff_profiles (status, created_at desc);

create table public.staff_invitations (
  id uuid primary key default gen_random_uuid(),
  staff_profile_id uuid not null references public.staff_profiles(id) on delete cascade,
  email text not null,
  intended_role public.app_role not null,
  status text not null default 'PENDING'
    check (status in ('PENDING','ACCEPTED','EXPIRED','REVOKED')),
  invited_by uuid references public.profiles(id) on delete set null,
  invited_at timestamptz not null default now(),
  expires_at timestamptz not null,
  accepted_at timestamptz,
  revoked_at timestamptz,
  supabase_user_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index uq_pending_staff_invitation_email
  on public.staff_invitations (lower(email)) where status = 'PENDING';
create index idx_staff_invitations_status on public.staff_invitations (status, invited_at desc);

create trigger trg_staff_profiles_updated_at before update on public.staff_profiles
for each row execute function public.set_updated_at();

create trigger trg_staff_invitations_updated_at before update on public.staff_invitations
for each row execute function public.set_updated_at();
