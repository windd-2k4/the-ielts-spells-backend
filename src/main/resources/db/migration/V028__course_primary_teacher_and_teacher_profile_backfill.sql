-- A course has one primary teacher. Additional rows are retained for teachers
-- who cover individual sessions and therefore need course-scoped access.
alter table public.course_teachers
  add column is_primary boolean not null default false;

-- Staff invitations existed before teacher_profiles was synchronized. Backfill
-- all teacher accounts that already have a public profile so current staff can
-- be assigned without recreating their Supabase Auth user.
insert into public.teacher_profiles (user_id, teacher_code)
select staff.auth_user_id,
       'GV-' || replace(staff.auth_user_id::text, '-', '')
from public.staff_profiles staff
join public.profiles profile on profile.id = staff.auth_user_id
where staff.auth_user_id is not null
  and staff.primary_role = 'TEACHER'::public.app_role
on conflict (user_id) do nothing;

-- Preserve teachers already selected on sessions as course-level members.
insert into public.course_teachers (course_id, teacher_id, teaching_role, assigned_at, is_primary)
select distinct session.course_id,
       session.teacher_id,
       'TEACHER'::public.app_role,
       now(),
       false
from public.course_sessions session
where session.teacher_id is not null
on conflict (course_id, teacher_id) do nothing;

-- Existing courses may already have multiple teacher rows. Select one stable
-- primary teacher, preferring the teacher used by the earliest session.
with ranked as (
  select teacher.course_id,
         teacher.teacher_id,
         row_number() over (
           partition by teacher.course_id
           order by coalesce((
             select min(session.starts_at)
             from public.course_sessions session
             where session.course_id = teacher.course_id
               and session.teacher_id = teacher.teacher_id
           ), 'infinity'::timestamptz),
           teacher.assigned_at,
           teacher.teacher_id
         ) as position
  from public.course_teachers teacher
)
update public.course_teachers teacher
set is_primary = true
from ranked
where teacher.course_id = ranked.course_id
  and teacher.teacher_id = ranked.teacher_id
  and ranked.position = 1;

create unique index uq_course_teachers_one_primary
  on public.course_teachers(course_id)
  where is_primary;

create index idx_course_teachers_teacher
  on public.course_teachers(teacher_id, assigned_at desc);

comment on column public.course_teachers.is_primary is
  'True for the default course teacher. Other rows are session substitutes with course-scoped access.';

