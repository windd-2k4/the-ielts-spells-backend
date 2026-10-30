-- A session-scoped test assignment is also a course progress activity.
-- Keep online attempts and manually uploaded evidence in the same progress model.

create unique index if not exists uq_course_activities_test_assignment
  on public.course_activities(test_assignment_id)
  where test_assignment_id is not null;

create or replace function public.ensure_test_assignment_course_activity(p_assignment_id uuid)
returns uuid
language plpgsql
as $$
declare
  v_course_id uuid;
  v_session_id uuid;
  v_actor_id uuid;
  v_opens_at timestamptz;
  v_closes_at timestamptz;
  v_title text;
  v_skill public.skill_type;
  v_program_id uuid;
  v_course_code text;
  v_course_name text;
  v_module_id uuid;
  v_learning_activity_id uuid;
  v_course_activity_id uuid;
begin
  select assignment.course_id, assignment.session_id, assignment.assigned_by,
         assignment.opens_at, assignment.closes_at, version.title, version.primary_skill,
         course.program_id, course.code, course.name
    into v_course_id, v_session_id, v_actor_id, v_opens_at, v_closes_at, v_title, v_skill,
         v_program_id, v_course_code, v_course_name
  from public.test_assignments assignment
  join public.test_versions version on version.id = assignment.test_version_id
  join public.courses course on course.id = assignment.course_id
  where assignment.id = p_assignment_id and assignment.archived_at is null;

  if v_course_id is null or v_session_id is null then
    return null;
  end if;

  select id into v_course_activity_id
  from public.course_activities
  where test_assignment_id = p_assignment_id
  limit 1;
  if v_course_activity_id is not null then
    return v_course_activity_id;
  end if;

  select activity.id into v_course_activity_id
  from public.course_activities activity
  join public.learning_activities learning on learning.id = activity.activity_id
  where activity.course_id = v_course_id and activity.session_id = v_session_id
    and activity.is_published = true
    and activity.assignment_id is null and activity.test_assignment_id is null
    and learning.skill = v_skill
  order by activity.created_at, activity.id
  limit 1;

  if v_course_activity_id is not null then
    update public.course_activities
    set test_assignment_id = p_assignment_id,
        opens_at = coalesce(opens_at, v_opens_at),
        due_at = coalesce(due_at, v_closes_at),
        updated_at = now()
    where id = v_course_activity_id;
    return v_course_activity_id;
  end if;

  if v_program_id is null then
    insert into public.course_programs(
      code, name, description, total_sessions, is_public, is_active, created_by
    )
    select 'AUTO-' || course.code, course.name || ' - Lộ trình', course.description,
           course.total_sessions, false, true, v_actor_id
    from public.courses course
    where course.id = v_course_id
    on conflict (code) do update set updated_at = now()
    returning id into v_program_id;

    update public.courses
    set program_id = v_program_id, updated_at = now()
    where id = v_course_id and program_id is null;
  end if;

  select id into v_module_id
  from public.course_modules
  where program_id = v_program_id and skill = v_skill
  order by display_order, created_at
  limit 1;

  if v_module_id is null then
    insert into public.course_modules(program_id, title, skill, display_order, is_required)
    select v_program_id, initcap(lower(v_skill::text)), v_skill,
           coalesce(max(display_order), -1) + 1, true
    from public.course_modules
    where program_id = v_program_id
    returning id into v_module_id;
  end if;

  insert into public.learning_activities(
    module_id, title, description, skill, activity_type, completion_method,
    display_order, is_required, requires_error_analysis,
    requires_comprehension_rating, allowed_evidence_types, created_by
  )
  select v_module_id, v_title, 'Bài được giao từ Ngân hàng đề.', v_skill,
         'ONLINE_TEST'::public.activity_type, 'WEB_OR_MANUAL'::public.completion_method,
         coalesce(max(display_order), -1) + 1, true, true, true,
         array['image/png', 'image/jpeg', 'image/webp'], v_actor_id
  from public.learning_activities
  where module_id = v_module_id
  returning id into v_learning_activity_id;

  insert into public.course_activities(
    course_id, session_id, activity_id, test_assignment_id, opens_at, due_at,
    is_required_override, is_published, created_by
  ) values (
    v_course_id, v_session_id, v_learning_activity_id, p_assignment_id,
    v_opens_at, v_closes_at, true, true, v_actor_id
  )
  returning id into v_course_activity_id;

  return v_course_activity_id;
end;
$$;

create or replace function public.ensure_test_assignment_course_activity_trigger()
returns trigger
language plpgsql
as $$
begin
  if new.session_id is not null and new.archived_at is null then
    perform public.ensure_test_assignment_course_activity(new.id);
  end if;
  return new;
end;
$$;

drop trigger if exists trg_test_assignments_course_activity on public.test_assignments;
create trigger trg_test_assignments_course_activity
  after insert or update of session_id, archived_at on public.test_assignments
  for each row execute function public.ensure_test_assignment_course_activity_trigger();

create or replace function public.sync_test_attempt_to_course_progress(p_attempt_id uuid)
returns void
language plpgsql
as $$
declare
  v_attempt public.test_attempts%rowtype;
  v_course_activity_id uuid;
  v_status public.activity_attempt_status;
  v_review_status public.review_status;
  v_total integer;
  v_answered integer;
  v_correct integer;
  v_max_score numeric(7,2);
  v_duration integer;
  v_late boolean;
begin
  select * into v_attempt from public.test_attempts where id = p_attempt_id;
  if v_attempt.id is null or v_attempt.attempt_origin <> 'ASSIGNMENT'
     or v_attempt.test_assignment_id is null then
    return;
  end if;

  v_course_activity_id := public.ensure_test_assignment_course_activity(v_attempt.test_assignment_id);
  if v_course_activity_id is null then
    return;
  end if;

  select count(*)::int, coalesce(sum(max_score), 0)
    into v_total, v_max_score
  from public.test_version_questions
  where test_version_id = v_attempt.test_version_id;

  select count(*) filter (
           where jsonb_typeof(normalized_answer) = 'array' and jsonb_array_length(normalized_answer) > 0
         )::int,
         count(*) filter (where is_correct = true)::int
    into v_answered, v_correct
  from public.test_attempt_responses
  where attempt_id = v_attempt.id;

  v_status := case v_attempt.status::text
    when 'IN_PROGRESS' then 'IN_PROGRESS'::public.activity_attempt_status
    when 'SUBMITTED' then 'WAITING_REVIEW'::public.activity_attempt_status
    when 'GRADED' then 'COMPLETED'::public.activity_attempt_status
    else 'CANCELLED'::public.activity_attempt_status
  end;
  v_review_status := case v_attempt.status::text
    when 'SUBMITTED' then 'PENDING'::public.review_status
    else 'NOT_REQUIRED'::public.review_status
  end;
  v_duration := case when v_attempt.submitted_at is null then null
    else greatest(0, extract(epoch from (v_attempt.submitted_at - v_attempt.started_at))::int) end;
  select coalesce(v_attempt.submitted_at > assignment.closes_at, false)
    into v_late
  from public.test_assignments assignment
  where assignment.id = v_attempt.test_assignment_id;

  insert into public.student_activity_attempts(
    class_activity_id, student_id, attempt_no, source, status, review_status,
    test_attempt_id, score, max_score, correct_count, incorrect_count,
    unanswered_count, duration_seconds, started_at, submitted_at, completed_at,
    is_late, metadata
  ) values (
    v_course_activity_id, v_attempt.student_id, v_attempt.attempt_no,
    'WEB'::public.result_source, v_status, v_review_status, v_attempt.id,
    coalesce(v_attempt.final_score, v_attempt.auto_score), nullif(v_max_score, 0),
    v_correct, greatest(v_answered - v_correct, 0), greatest(v_total - v_answered, 0),
    v_duration, v_attempt.started_at, v_attempt.submitted_at,
    case when v_status = 'COMPLETED' then v_attempt.submitted_at else null end,
    v_late, jsonb_build_object('testAssignmentId', v_attempt.test_assignment_id)
  )
  on conflict (class_activity_id, student_id, attempt_no) do update
  set source = 'WEB'::public.result_source,
      status = case
        when excluded.status = 'IN_PROGRESS'::public.activity_attempt_status
          and student_activity_attempts.completed_at is not null
          then student_activity_attempts.status
        else excluded.status
      end,
      review_status = case
        when excluded.status = 'IN_PROGRESS'::public.activity_attempt_status
          and student_activity_attempts.completed_at is not null
          then student_activity_attempts.review_status
        else excluded.review_status
      end,
      test_attempt_id = excluded.test_attempt_id,
      score = excluded.score,
      max_score = excluded.max_score,
      correct_count = excluded.correct_count,
      incorrect_count = excluded.incorrect_count,
      unanswered_count = excluded.unanswered_count,
      duration_seconds = excluded.duration_seconds,
      started_at = excluded.started_at,
      submitted_at = coalesce(excluded.submitted_at, student_activity_attempts.submitted_at),
      completed_at = coalesce(excluded.completed_at, student_activity_attempts.completed_at),
      is_late = excluded.is_late,
      metadata = student_activity_attempts.metadata || excluded.metadata,
      updated_at = now();
end;
$$;

create or replace function public.sync_test_attempt_to_course_progress_trigger()
returns trigger
language plpgsql
as $$
begin
  perform public.sync_test_attempt_to_course_progress(new.id);
  return new;
end;
$$;

drop trigger if exists trg_test_attempts_course_progress on public.test_attempts;
create trigger trg_test_attempts_course_progress
  after insert or update of status, submitted_at, auto_score, final_score on public.test_attempts
  for each row execute function public.sync_test_attempt_to_course_progress_trigger();

select public.ensure_test_assignment_course_activity(id)
from public.test_assignments
where session_id is not null and archived_at is null;

select public.sync_test_attempt_to_course_progress(id)
from public.test_attempts
where attempt_origin = 'ASSIGNMENT' and test_assignment_id is not null;
