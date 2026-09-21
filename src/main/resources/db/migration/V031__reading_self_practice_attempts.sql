-- Published Reading tests may be attempted independently from course assignments.
-- Both assignment and self-practice attempts remain pinned to an immutable version.

alter table public.test_attempts
  alter column test_assignment_id drop not null,
  add column if not exists attempt_origin text not null default 'ASSIGNMENT';

alter table public.test_attempts
  drop constraint if exists test_attempts_origin_check,
  add constraint test_attempts_origin_check check (
    (attempt_origin = 'ASSIGNMENT' and test_assignment_id is not null)
    or (attempt_origin = 'SELF_PRACTICE' and test_assignment_id is null)
  );

create unique index if not exists uq_test_attempts_active_self_practice
  on public.test_attempts(student_id, test_version_id)
  where attempt_origin = 'SELF_PRACTICE' and status = 'IN_PROGRESS';

create index if not exists idx_test_attempts_self_practice_history
  on public.test_attempts(student_id, test_version_id, started_at desc)
  where attempt_origin = 'SELF_PRACTICE';

create or replace function public.enforce_test_attempt_version()
returns trigger
language plpgsql
as $$
begin
  if new.test_version_id is null then
    raise exception 'A test attempt must pin a test version';
  end if;

  if new.attempt_origin = 'ASSIGNMENT' then
    if new.test_assignment_id is null or not exists (
      select 1
      from public.test_assignments assignment
      where assignment.id = new.test_assignment_id
        and assignment.test_version_id = new.test_version_id
    ) then
      raise exception 'The test attempt version must match its assignment version';
    end if;
  elsif new.attempt_origin = 'SELF_PRACTICE' then
    if new.test_assignment_id is not null then
      raise exception 'A self-practice attempt cannot reference an assignment';
    end if;
  else
    raise exception 'Unsupported test attempt origin';
  end if;

  return new;
end;
$$;

drop trigger if exists trg_test_attempts_enforce_version on public.test_attempts;
create trigger trg_test_attempts_enforce_version
  before insert or update of test_assignment_id, test_version_id, attempt_origin on public.test_attempts
  for each row execute function public.enforce_test_attempt_version();

comment on column public.test_attempts.attempt_origin is
  'ASSIGNMENT for course-delivered work; SELF_PRACTICE for student-initiated published Reading tests.';
