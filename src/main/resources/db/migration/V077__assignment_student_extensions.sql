create table if not exists public.assignment_student_extensions (
  id uuid primary key default gen_random_uuid(),
  assignment_id uuid not null references public.assignments(id) on delete cascade,
  class_activity_id uuid not null references public.course_activities(id) on delete cascade,
  student_id uuid not null references public.student_profiles(user_id) on delete cascade,
  extension_due_at timestamptz not null,
  reason text,
  granted_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint assignment_student_extensions_unique unique (assignment_id, student_id)
);

create index if not exists idx_assignment_student_extensions_activity_student
  on public.assignment_student_extensions(class_activity_id, student_id);

alter table public.assignment_student_extensions enable row level security;
