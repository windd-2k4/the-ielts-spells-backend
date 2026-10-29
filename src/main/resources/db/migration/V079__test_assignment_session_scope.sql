alter table public.test_assignments
  add column if not exists session_id uuid references public.course_sessions(id) on delete set null;

create index if not exists idx_test_assignments_course_session
  on public.test_assignments(course_id, session_id, created_at desc);

comment on column public.test_assignments.session_id is
  'Optional course session scope for teacher-assigned tests. The pinned test version remains the source for every attempt.';
