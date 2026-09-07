-- Keep editable drafts separate from immutable versions used by published delivery.
-- Existing published tests are backfilled as version 1 so their historical content
-- remains available before the student-assignment flow starts referencing versions.

alter table public.tests
  add column if not exists draft_revision integer not null default 1,
  add column if not exists current_published_version_id uuid;

create table if not exists public.test_versions (
  id uuid primary key default gen_random_uuid(),
  test_id uuid not null references public.tests(id) on delete restrict,
  version_number integer not null check (version_number > 0),
  version_label text not null,
  title text not null,
  description text,
  duration_minutes smallint not null check (duration_minutes > 0),
  primary_skill public.skill_type not null,
  test_type text not null check (test_type in ('FULL_TEST', 'SINGLE_SKILL')),
  tags jsonb not null default '[]'::jsonb,
  builder_content jsonb not null default '{}'::jsonb,
  published_by uuid not null references public.profiles(id) on delete restrict,
  published_at timestamptz not null default now(),
  unique (test_id, version_number)
);

alter table public.tests
  drop constraint if exists tests_current_published_version_fk,
  add constraint tests_current_published_version_fk
    foreign key (current_published_version_id)
    references public.test_versions(id)
    on delete restrict;

insert into public.test_versions (
  test_id, version_number, version_label, title, description, duration_minutes,
  primary_skill, test_type, tags, builder_content, published_by, published_at
)
select
  test.id,
  1,
  coalesce(nullif(test.version, ''), 'v1.0'),
  test.title,
  test.description,
  coalesce(test.duration_minutes, 60),
  test.primary_skill,
  test.test_type,
  test.tags,
  test.builder_content,
  test.created_by,
  coalesce(test.updated_at, test.created_at, now())
from public.tests test
where test.status = 'PUBLISHED'
  and not exists (
    select 1 from public.test_versions version
    where version.test_id = test.id
  );

update public.tests test
set current_published_version_id = version.id
from public.test_versions version
where version.test_id = test.id
  and test.current_published_version_id is null
  and version.version_number = (
    select max(latest.version_number)
    from public.test_versions latest
    where latest.test_id = test.id
  );

alter table public.test_assignments
  add column if not exists test_version_id uuid;

alter table public.test_assignments
  drop constraint if exists test_assignments_test_version_fk,
  add constraint test_assignments_test_version_fk
    foreign key (test_version_id)
    references public.test_versions(id)
    on delete restrict;

update public.test_assignments assignment
set test_version_id = test.current_published_version_id
from public.tests test
where assignment.test_id = test.id
  and assignment.test_version_id is null
  and test.current_published_version_id is not null;

create index if not exists idx_test_versions_test_published
  on public.test_versions(test_id, version_number desc);

create index if not exists idx_test_assignments_test_version
  on public.test_assignments(test_version_id)
  where test_version_id is not null;
