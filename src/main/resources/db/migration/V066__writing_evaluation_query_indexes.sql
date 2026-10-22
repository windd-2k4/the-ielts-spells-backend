create index if not exists idx_writing_evaluations_created_at
  on public.writing_evaluations(created_at desc);

create index if not exists idx_writing_evaluations_teacher_created_at
  on public.writing_evaluations(teacher_status, created_at desc);

create index if not exists idx_test_assignments_course
  on public.test_assignments(course_id, id);
