create table public.access_requests (
  id uuid primary key default gen_random_uuid(), full_name text not null, email text not null,
  phone text, requested_role public.app_role not null, reason text,
  status text not null default 'PENDING' check (status in ('PENDING','APPROVED','REJECTED','CANCELLED')),
  reviewed_by uuid references public.profiles(id) on delete set null, reviewed_at timestamptz,
  review_note text, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  constraint access_requests_management_role_check check (requested_role in ('teacher','teaching_assistant','admissions','cms_editor'))
);
create unique index uq_access_requests_pending_email on public.access_requests (lower(email)) where status = 'PENDING';
create index idx_access_requests_status_created on public.access_requests (status, created_at desc);
