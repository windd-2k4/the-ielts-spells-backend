alter table public.assignments
  add column if not exists late_policy text not null default 'MARK_LATE',
  add column if not exists late_grace_minutes integer not null default 0,
  add column if not exists reflection_due_at timestamptz;

alter table public.assignments
  drop constraint if exists assignments_late_policy_check;

alter table public.assignments
  add constraint assignments_late_policy_check
    check (late_policy in ('ALLOW', 'MARK_LATE', 'BLOCK'));

alter table public.assignments
  drop constraint if exists assignments_late_grace_minutes_check;

alter table public.assignments
  add constraint assignments_late_grace_minutes_check
    check (late_grace_minutes >= 0 and late_grace_minutes <= 10080);

alter table public.teacher_activity_reviews
  add column if not exists reflection_due_at timestamptz;

create table if not exists public.assignment_contact_logs (
  id uuid primary key default gen_random_uuid(),
  course_id uuid not null references public.courses(id) on delete cascade,
  assignment_id uuid not null references public.assignments(id) on delete cascade,
  class_activity_id uuid not null references public.course_activities(id) on delete cascade,
  student_id uuid not null references public.student_profiles(user_id) on delete cascade,
  actor_id uuid not null references public.profiles(id) on delete restrict,
  channel text not null check (channel in ('IN_APP', 'MANUAL', 'EMAIL')),
  action text not null check (action in ('REMINDER_SENT', 'CONTACT_LOGGED')),
  message text not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_assignment_contact_logs_activity
  on public.assignment_contact_logs(class_activity_id, created_at desc);

alter table public.assignment_contact_logs enable row level security;
