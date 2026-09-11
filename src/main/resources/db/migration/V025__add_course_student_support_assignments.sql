-- Student Support staff operate only within courses explicitly assigned to
-- them. This relation is deliberately separate from course_teachers: it
-- grants learner-support visibility, not teaching or academic-authoring work.
create table public.course_student_supports (
  course_id uuid not null references public.courses(id) on delete cascade,
  student_support_id uuid not null references public.profiles(id) on delete cascade,
  assigned_by uuid references public.profiles(id) on delete set null,
  assigned_at timestamptz not null default now(),
  primary key (course_id, student_support_id)
);

create index idx_course_student_supports_support
  on public.course_student_supports(student_support_id, assigned_at desc);

comment on table public.course_student_supports is
  'Course-scoped Student Support assignments. Assignment is validated against active STAFF_PROFILE role STUDENT_SUPPORT by the application.';
