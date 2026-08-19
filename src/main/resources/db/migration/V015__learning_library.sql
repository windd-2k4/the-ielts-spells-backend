-- Structured learning library for reusable materials and exercise templates.
create sequence public.learning_resource_code_seq start 1;
create sequence public.exercise_template_code_seq start 1;

create table public.learning_resources (
  id uuid primary key default gen_random_uuid(),
  code text not null unique default ('RES-' || lpad(nextval('public.learning_resource_code_seq')::text, 6, '0')),
  title text not null,
  description text,
  skill public.skill_type not null,
  category text not null,
  resource_type text not null check (resource_type in (
    'DOCUMENT', 'AUDIO', 'VIDEO', 'DRIVE_LINK', 'TEACHER_NOTE', 'ANSWER_KEY', 'VOCABULARY'
  )),
  scope text not null check (scope in ('GLOBAL', 'COURSE')),
  course_id uuid references public.courses(id) on delete cascade,
  external_url text not null,
  teacher_only boolean not null default false,
  status text not null default 'PUBLISHED' check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  created_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((scope = 'GLOBAL' and course_id is null) or (scope = 'COURSE' and course_id is not null))
);

create table public.exercise_templates (
  id uuid primary key default gen_random_uuid(),
  code text not null unique default ('EX-' || lpad(nextval('public.exercise_template_code_seq')::text, 6, '0')),
  title text not null,
  instructions text,
  skill public.skill_type not null,
  category text not null,
  exercise_type text not null,
  completion_mode text not null check (completion_mode in ('WEB', 'DRIVE_LINK', 'FILE_UPLOAD', 'MANUAL', 'HYBRID')),
  scope text not null check (scope in ('GLOBAL', 'COURSE')),
  course_id uuid references public.courses(id) on delete cascade,
  source_url text,
  duration_minutes smallint check (duration_minutes is null or duration_minutes > 0),
  max_score numeric(7,2) check (max_score is null or max_score > 0),
  attempt_limit smallint not null default 1 check (attempt_limit > 0),
  requires_teacher_review boolean not null default false,
  content jsonb not null default '{}'::jsonb,
  answer_key jsonb not null default '{}'::jsonb,
  status text not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  created_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((scope = 'GLOBAL' and course_id is null) or (scope = 'COURSE' and course_id is not null))
);

create index idx_learning_resources_filter
  on public.learning_resources(skill, scope, course_id, status, category);
create index idx_exercise_templates_filter
  on public.exercise_templates(skill, scope, course_id, status, category);

create trigger trg_learning_resources_updated_at
  before update on public.learning_resources
  for each row execute function public.set_updated_at();
create trigger trg_exercise_templates_updated_at
  before update on public.exercise_templates
  for each row execute function public.set_updated_at();

alter table public.learning_resources enable row level security;
alter table public.exercise_templates enable row level security;

-- A session may contain teaching materials without a deadline, while reusable
-- exercise templates become normal assignments with the existing +2 day rule.
alter table public.course_session_items
  drop constraint if exists course_session_items_item_type_check,
  drop constraint if exists course_session_items_check,
  alter column deadline_at drop not null,
  add column source_resource_id uuid references public.learning_resources(id) on delete set null,
  add column source_exercise_template_id uuid references public.exercise_templates(id) on delete set null,
  add column is_required boolean not null default true,
  add column visibility text not null default 'STUDENT' check (visibility in ('STUDENT', 'TEACHER'));

alter table public.course_session_items
  add constraint course_session_items_item_type_check
    check (item_type in ('MATERIAL', 'ASSIGNMENT', 'TEST')),
  add constraint course_session_items_source_check check (
    (item_type = 'MATERIAL' and source_assignment_id is null and source_test_id is null
      and source_exercise_template_id is null)
    or
    (item_type = 'ASSIGNMENT' and source_test_id is null and source_resource_id is null)
    or
    (item_type = 'TEST' and source_assignment_id is null and source_resource_id is null
      and source_exercise_template_id is null)
  ),
  add constraint course_session_items_deadline_check
    check (item_type = 'MATERIAL' or deadline_at is not null);

create index idx_course_session_items_resource on public.course_session_items(source_resource_id);
create index idx_course_session_items_exercise_template on public.course_session_items(source_exercise_template_id);

create or replace function public.enforce_course_session_item_limit()
returns trigger
language plpgsql
as $$
begin
  if new.item_type in ('ASSIGNMENT', 'TEST') and (
    select count(*)
    from public.course_session_items
    where session_id = new.session_id
      and item_type in ('ASSIGNMENT', 'TEST')
      and (tg_op = 'INSERT' or id <> new.id)
  ) >= 10 then
    raise exception 'Each course session can contain at most 10 assignments or tests';
  end if;
  return new;
end;
$$;

comment on table public.learning_resources is
  'Reusable Google Drive or web learning materials, globally shared or owned by one course.';
comment on table public.exercise_templates is
  'Reusable structured exercise definitions for session assignments and the future learner practice web.';
comment on column public.course_session_items.visibility is
  'TEACHER hides teacher notes and internal materials from learners.';
