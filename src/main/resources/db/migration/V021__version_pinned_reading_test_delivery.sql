-- Deliverable Reading tests are version-pinned. Published test versions are
-- immutable, and their Reading structure is materialized for student delivery.

alter table public.test_assignments
  add column if not exists mode text not null default 'PRACTICE'
    check (mode in ('PRACTICE', 'EXAM')),
  add column if not exists duration_seconds integer
    check (duration_seconds is null or duration_seconds > 0),
  add column if not exists show_result_after_submit boolean not null default true,
  add column if not exists archived_at timestamptz,
  add column if not exists updated_at timestamptz not null default now();

-- V019 supplied a nullable column and backfilled assignments whose test had a
-- published version. Existing legacy rows may remain nullable, but new writes
-- are rejected by the trigger below until an operator explicitly resolves them.
alter table public.test_assignments
  drop constraint if exists test_assignments_test_id_class_id_key,
  drop constraint if exists test_assignments_test_id_course_id_key;

create unique index if not exists uq_test_assignments_active_version_course
  on public.test_assignments(test_version_id, course_id)
  where archived_at is null and test_version_id is not null;

create or replace function public.enforce_test_assignment_version()
returns trigger
language plpgsql
as $$
begin
  if new.test_version_id is null then
    raise exception 'A test assignment must pin a published test version';
  end if;

  if not exists (
    select 1
    from public.test_versions version
    where version.id = new.test_version_id
      and version.test_id = new.test_id
  ) then
    raise exception 'The test version must belong to the assigned test';
  end if;

  if tg_op = 'UPDATE'
    and new.test_version_id is distinct from old.test_version_id
    and exists (
      select 1 from public.test_attempts attempt
      where attempt.test_assignment_id = old.id
    ) then
    raise exception 'Cannot change the version of an assignment that has attempts';
  end if;

  return new;
end;
$$;

drop trigger if exists trg_test_assignments_enforce_version on public.test_assignments;
create trigger trg_test_assignments_enforce_version
  before insert or update of test_id, test_version_id on public.test_assignments
  for each row execute function public.enforce_test_assignment_version();

alter table public.test_attempts
  add column if not exists test_version_id uuid references public.test_versions(id) on delete restrict,
  add column if not exists expires_at timestamptz,
  add column if not exists last_saved_at timestamptz not null default now(),
  add column if not exists updated_at timestamptz not null default now();

update public.test_attempts attempt
set test_version_id = assignment.test_version_id
from public.test_assignments assignment
where assignment.id = attempt.test_assignment_id
  and attempt.test_version_id is null
  and assignment.test_version_id is not null;

create index if not exists idx_test_attempts_student_status
  on public.test_attempts(student_id, status, started_at desc);
create index if not exists idx_test_attempts_assignment_student
  on public.test_attempts(test_assignment_id, student_id, attempt_no desc);

create or replace function public.enforce_test_attempt_version()
returns trigger
language plpgsql
as $$
begin
  if new.test_version_id is null then
    raise exception 'A test attempt must pin the assignment test version';
  end if;

  if not exists (
    select 1
    from public.test_assignments assignment
    where assignment.id = new.test_assignment_id
      and assignment.test_version_id = new.test_version_id
  ) then
    raise exception 'The test attempt version must match its assignment version';
  end if;

  return new;
end;
$$;

drop trigger if exists trg_test_attempts_enforce_version on public.test_attempts;
create trigger trg_test_attempts_enforce_version
  before insert or update of test_assignment_id, test_version_id on public.test_attempts
  for each row execute function public.enforce_test_attempt_version();

create table if not exists public.test_version_sections (
  id uuid primary key default gen_random_uuid(),
  test_version_id uuid not null references public.test_versions(id) on delete cascade,
  section_key text not null,
  section_no smallint not null check (section_no > 0),
  title text not null,
  content_html text not null default '',
  display_order smallint not null check (display_order >= 0),
  unique (test_version_id, section_key),
  unique (test_version_id, section_no)
);

create table if not exists public.test_version_question_groups (
  id uuid primary key default gen_random_uuid(),
  test_version_id uuid not null references public.test_versions(id) on delete cascade,
  section_id uuid not null references public.test_version_sections(id) on delete cascade,
  group_key text not null,
  title text not null,
  type_format text not null,
  instructions text not null,
  answer_config jsonb not null default '{}'::jsonb,
  display_order smallint not null check (display_order >= 0),
  unique (test_version_id, group_key)
);

create table if not exists public.test_version_questions (
  id uuid primary key default gen_random_uuid(),
  test_version_id uuid not null references public.test_versions(id) on delete cascade,
  group_id uuid not null references public.test_version_question_groups(id) on delete cascade,
  question_key text not null,
  question_no smallint not null check (question_no > 0),
  type_format text not null,
  prompt text not null,
  correct_answers jsonb not null default '[]'::jsonb,
  acceptable_answers jsonb not null default '[]'::jsonb,
  explanation text,
  max_score numeric(6,2) not null default 1 check (max_score > 0),
  display_order smallint not null check (display_order >= 0),
  unique (test_version_id, question_key),
  unique (test_version_id, question_no)
);

create table if not exists public.test_version_question_options (
  id uuid primary key default gen_random_uuid(),
  test_version_id uuid not null references public.test_versions(id) on delete cascade,
  group_id uuid not null references public.test_version_question_groups(id) on delete cascade,
  question_id uuid references public.test_version_questions(id) on delete cascade,
  option_key text not null,
  option_code text,
  content text not null,
  display_order smallint not null check (display_order >= 0),
  unique (test_version_id, option_key)
);

create index if not exists idx_test_version_sections_delivery
  on public.test_version_sections(test_version_id, display_order);
create index if not exists idx_test_version_groups_delivery
  on public.test_version_question_groups(section_id, display_order);
create index if not exists idx_test_version_questions_delivery
  on public.test_version_questions(group_id, display_order);
create index if not exists idx_test_version_options_delivery
  on public.test_version_question_options(group_id, question_id, display_order);

create table if not exists public.test_attempt_responses (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.test_attempts(id) on delete cascade,
  question_key text not null,
  question_type text not null,
  answer jsonb not null default '{}'::jsonb,
  normalized_answer jsonb not null default '[]'::jsonb,
  is_correct boolean,
  auto_score numeric(6,2),
  max_score numeric(6,2) not null default 1 check (max_score > 0),
  client_revision integer not null default 0 check (client_revision >= 0),
  answered_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (attempt_id, question_key)
);

create index if not exists idx_test_attempt_responses_attempt
  on public.test_attempt_responses(attempt_id, answered_at);

create or replace function public.reject_test_version_mutation()
returns trigger
language plpgsql
as $$
begin
  raise exception 'Published test versions are immutable';
end;
$$;

drop trigger if exists trg_test_versions_immutable on public.test_versions;
create trigger trg_test_versions_immutable
  before update or delete on public.test_versions
  for each row execute function public.reject_test_version_mutation();

comment on column public.test_assignments.test_version_id is
  'Immutable published version used for all delivery attempts; legacy null rows cannot be used by new delivery APIs.';
comment on table public.test_attempt_responses is
  'Version-keyed student answers for Test Builder delivery; separate from legacy test_answers/question tables.';
