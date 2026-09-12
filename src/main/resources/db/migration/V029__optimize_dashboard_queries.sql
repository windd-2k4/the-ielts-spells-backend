-- Dashboard reads favor small, ordered projections over loading full entities.
create index if not exists idx_courses_dashboard
  on public.courses(is_active, status, starts_on);

create index if not exists idx_enrollments_active_dashboard
  on public.enrollments(student_id)
  where status = 'ACTIVE';

create index if not exists idx_course_sessions_course_status_time
  on public.course_sessions(course_id, status, starts_at);

create index if not exists idx_learning_resources_recent_active
  on public.learning_resources(updated_at desc)
  where status <> 'ARCHIVED';

create index if not exists idx_learning_resource_files_active
  on public.learning_resource_files(resource_id)
  where archived_at is null;

create index if not exists idx_test_assignments_active_course
  on public.test_assignments(course_id, created_at desc)
  where archived_at is null;
