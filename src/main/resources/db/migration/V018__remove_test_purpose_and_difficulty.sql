-- Tests in the bank are reusable content units. Placement, mock, review, or
-- practice intent belongs to the assignment/exam assembly workflow, not the test.
drop index if exists public.idx_tests_content_hub;

alter table public.tests
  drop constraint if exists tests_purpose_check,
  drop column if exists purpose,
  drop column if exists difficulty;

create index if not exists idx_tests_content_hub
  on public.tests(status, primary_skill, test_type, updated_at desc);
