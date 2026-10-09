-- A session assignment may point directly at one published test-bank entry.
-- This keeps practice tests inside normal teaching sessions without turning
-- the whole session into a standalone exam event.
alter table public.course_session_items
  drop constraint if exists course_session_items_source_check;

alter table public.course_session_items
  add constraint course_session_items_source_check check (
    (item_type = 'MATERIAL'
      and source_assignment_id is null
      and source_test_id is null
      and source_exercise_template_id is null)
    or
    (item_type = 'ASSIGNMENT'
      and source_resource_id is null
      and num_nonnulls(source_assignment_id, source_test_id, source_exercise_template_id) <= 1)
    or
    (item_type = 'TEST'
      and source_assignment_id is null
      and source_resource_id is null
      and source_exercise_template_id is null)
  );
