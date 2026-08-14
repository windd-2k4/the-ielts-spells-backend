alter type public.attendance_status add value if not exists 'PENDING';

create table public.attendance_sheets (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null unique references public.course_sessions(id) on delete cascade,
  status varchar(20) not null default 'DRAFT',
  prepared_by uuid,
  locked_by uuid,
  locked_at timestamptz,
  reopened_by uuid,
  reopened_at timestamptz,
  reopen_reason text,
  row_version integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_attendance_sheet_status check (status in ('DRAFT', 'LOCKED')),
  constraint ck_attendance_sheet_lock check (
    (status = 'DRAFT') or (locked_by is not null and locked_at is not null)
  )
);

create index idx_attendance_sheets_status on public.attendance_sheets(status, session_id);

alter table public.attendance_records add column if not exists updated_by uuid;

create table public.attendance_sheet_audits (
  id uuid primary key default gen_random_uuid(),
  sheet_id uuid not null references public.attendance_sheets(id) on delete cascade,
  action varchar(30) not null,
  actor_id uuid not null,
  reason text,
  created_at timestamptz not null default now(),
  constraint ck_attendance_sheet_audit_action check (action in ('INITIALIZED', 'DRAFT_SAVED', 'LOCKED', 'REOPENED'))
);

create index idx_attendance_sheet_audits_sheet on public.attendance_sheet_audits(sheet_id, created_at desc);

insert into public.attendance_sheets(session_id, status)
select distinct session_id, 'DRAFT'
from public.attendance_records
on conflict (session_id) do nothing;

create trigger trg_attendance_sheets_updated_at
before update on public.attendance_sheets
for each row execute function public.set_updated_at();
