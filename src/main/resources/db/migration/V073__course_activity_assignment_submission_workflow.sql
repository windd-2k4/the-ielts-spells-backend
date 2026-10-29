-- Connect the existing course activity workflow to generic written/audio assignments.
-- Test-bank assignments remain a separate, version-pinned delivery path.

alter table public.assignments
  drop constraint if exists assignments_assignment_type_check;

alter table public.assignments
  add constraint assignments_assignment_type_check check (
    assignment_type in ('general', 'listening', 'reading', 'writing', 'speaking', 'vocabulary')
  );

create unique index if not exists uq_course_activities_assignment
  on public.course_activities(assignment_id)
  where assignment_id is not null;

create index if not exists idx_teacher_activity_reviews_attempt_reviewed
  on public.teacher_activity_reviews(attempt_id, reviewed_at desc);
