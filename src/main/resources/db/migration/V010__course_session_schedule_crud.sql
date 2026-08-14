-- Enrich course sessions for timetable management.
alter table public.course_sessions
  add column phase_name text,
  add column content text,
  add column teacher_id uuid references public.teacher_profiles(user_id) on delete set null;

create index idx_course_sessions_teacher_time
  on public.course_sessions(teacher_id, starts_at);

-- A lightweight session-owned plan. source_* columns are intentionally nullable:
-- manual items work now, while the assignment/test libraries can be attached later.
create table public.course_session_items (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.course_sessions(id) on delete cascade,
  item_type text not null check (item_type in ('ASSIGNMENT', 'TEST')),
  title text not null,
  description text,
  source_assignment_id uuid references public.assignments(id) on delete set null,
  source_test_id uuid references public.tests(id) on delete set null,
  deadline_at timestamptz not null,
  display_order smallint not null default 0 check (display_order >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (
    (item_type = 'ASSIGNMENT' and source_test_id is null)
    or (item_type = 'TEST' and source_assignment_id is null)
  )
);

create index idx_course_session_items_session_order
  on public.course_session_items(session_id, display_order, created_at);

create or replace function public.enforce_course_session_item_limit()
returns trigger
language plpgsql
as $$
begin
  if (
    select count(*)
    from public.course_session_items
    where session_id = new.session_id
      and (tg_op = 'INSERT' or id <> new.id)
  ) >= 10 then
    raise exception 'Each course session can contain at most 10 assignments or tests';
  end if;
  return new;
end;
$$;

create trigger trg_course_session_item_limit
  before insert or update of session_id on public.course_session_items
  for each row execute function public.enforce_course_session_item_limit();

alter table public.course_session_items enable row level security;

create trigger trg_course_session_items_updated_at
  before update on public.course_session_items
  for each row execute function public.set_updated_at();

comment on column public.course_sessions.phase_name is
  'Course roadmap phase, for example Giai đoạn 1 or Giai đoạn 2.';
comment on column public.course_sessions.content is
  'Detailed teaching content and learning outcomes of the session.';
comment on column public.course_sessions.teacher_id is
  'Teacher responsible for this session.';
comment on table public.course_session_items is
  'Up to ten assignments or tests planned for a course session.';
