-- Unify the business concept exposed to users: a course is one concrete cohort.
-- The former reusable course row is retained as an internal curriculum program so
-- existing modules and learning activities can still be shared safely.

create type public.skill_pair as enum ('LISTENING_READING', 'SPEAKING_WRITING');

alter table public.courses rename to course_programs;
alter table public.classes rename to courses;
alter table public.courses rename column course_id to program_id;
alter table public.courses alter column program_id drop not null;

alter table public.courses
  add column description text,
  add column level text,
  add column skill_pair public.skill_pair,
  add column target_band numeric(2,1) check (target_band between 0 and 9),
  add column total_sessions smallint check (total_sessions > 0),
  add column tuition_amount numeric(12,2) check (tuition_amount is null or tuition_amount >= 0),
  add column is_public boolean not null default false,
  add column is_active boolean not null default true;

update public.courses c
set description = p.description,
    level = p.level,
    target_band = p.target_band,
    total_sessions = coalesce(p.total_sessions, 1),
    tuition_amount = p.tuition_amount,
    is_public = p.is_public,
    is_active = p.is_active,
    skill_pair = case
      when exists (
        select 1 from public.course_modules m
        where m.course_id = p.id and m.skill in ('WRITING', 'SPEAKING')
      ) and not exists (
        select 1 from public.course_modules m
        where m.course_id = p.id and m.skill in ('LISTENING', 'READING')
      ) then 'SPEAKING_WRITING'::public.skill_pair
      else 'LISTENING_READING'::public.skill_pair
    end
from public.course_programs p
where p.id = c.program_id;

update public.courses
set skill_pair = 'LISTENING_READING',
    total_sessions = coalesce(total_sessions, 1)
where skill_pair is null;

alter table public.courses
  alter column skill_pair set not null,
  alter column total_sessions set not null;

-- Curriculum now explicitly belongs to the internal program definition.
alter table public.course_modules rename column course_id to program_id;

-- Rename all cohort-owned objects and foreign-key columns to course terminology.
alter table public.class_teachers rename to course_teachers;
alter table public.course_teachers rename column class_id to course_id;

alter table public.enrollments rename column class_id to course_id;

alter table public.class_sessions rename to course_sessions;
alter table public.course_sessions rename column class_id to course_id;

alter table public.class_activities rename to course_activities;
alter table public.course_activities rename column class_id to course_id;

alter table public.assignments rename column class_id to course_id;
alter table public.test_assignments rename column class_id to course_id;

alter table public.enrollment_reservations rename column target_class_id to target_course_id;
alter table public.class_transfers rename to course_transfers;
alter table public.course_transfers rename column target_class_id to target_course_id;

-- Public discovery, when enabled, applies to concrete courses only. Curriculum
-- programs are an internal implementation detail and must not be exposed.
drop policy if exists courses_public_read on public.course_programs;
alter table public.courses enable row level security;
drop policy if exists courses_public_read on public.courses;
create policy courses_public_read on public.courses
  for select
  using (is_public = true and is_active = true);

-- Preserve old index definitions while giving new migrations predictable names.
alter index if exists idx_classes_course_status rename to idx_courses_program_status;
alter index if exists idx_enrollments_class_status rename to idx_enrollments_course_status;
alter index if exists idx_sessions_class_time rename to idx_sessions_course_time;
alter index if exists idx_class_activities_class_due rename to idx_course_activities_course_due;
alter index if exists idx_class_activities_session rename to idx_course_activities_session;
alter index if exists idx_assignments_class_due rename to idx_assignments_course_due;
alter index if exists uq_class_activities_with_session rename to uq_course_activities_with_session;
alter index if exists uq_class_activities_without_session rename to uq_course_activities_without_session;

comment on table public.courses is
  'A concrete IELTS course/cohort. It owns schedule, teachers, students, attendance and progress.';
comment on table public.course_programs is
  'Internal reusable curriculum program. It is not exposed as a separate Course/Class concept in the management UI.';
comment on column public.courses.skill_pair is
  'The only two supported skill combinations: LISTENING_READING or SPEAKING_WRITING.';
