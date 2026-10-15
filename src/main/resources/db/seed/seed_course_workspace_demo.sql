-- Idempotent workspace data for the SEED-LR-2609 course.
-- Uses the course's existing enrollment roster; it never creates or changes users.

begin;

create temporary table workspace_seed_context (
    course_id uuid primary key,
    actor_id uuid not null
) on commit drop;

insert into workspace_seed_context (course_id, actor_id)
select c.id, coalesce(c.created_by, (
    select p.id
    from public.profiles p
    join public.user_roles r on r.user_id = p.id
    where p.is_active = true and r.role = 'ADMIN'
    order by p.created_at
    limit 1
))
from public.courses c
where c.code = 'SEED-LR-2609';

do $$
begin
    if not exists (select 1 from workspace_seed_context) then
        raise exception 'Khong tim thay khoa hoc SEED-LR-2609.';
    end if;
    if exists (select 1 from workspace_seed_context where actor_id is null) then
        raise exception 'Khong tim thay tai khoan quan tri de gan du lieu demo.';
    end if;
end
$$;

insert into public.course_programs (
    id, code, name, description, level, target_band, total_sessions,
    is_public, is_active, created_by
)
select
    'a1000000-0000-4000-8000-000000000001'::uuid,
    'SEED-PROGRAM-LR-FOUNDATION',
    'IELTS Listening & Reading Foundation',
    'Chuong trinh mau cho workspace van hanh khoa hoc.',
    'Foundation', 5.5, 12, false, true, actor_id
from workspace_seed_context
on conflict (code) do update set
    name = excluded.name,
    description = excluded.description,
    level = excluded.level,
    target_band = excluded.target_band,
    total_sessions = excluded.total_sessions,
    is_active = true,
    updated_at = now();

update public.courses
set program_id = (select id from public.course_programs where code = 'SEED-PROGRAM-LR-FOUNDATION'),
    total_sessions = 12,
    updated_at = now()
where id = (select course_id from workspace_seed_context);

insert into public.course_sessions (
    course_id, session_no, title, starts_at, ends_at, zoom_url, status,
    phase_name, content, created_by
)
select
    ctx.course_id,
    value.session_no,
    value.title,
    value.starts_at,
    value.starts_at + interval '2 hours',
    'https://zoom.us/j/1234567890',
    value.status::public.session_status,
    value.phase_name,
    value.content,
    ctx.actor_id
from workspace_seed_context ctx
cross join (values
    (1,  'Diagnostic Listening & Reading',       timestamptz '2026-09-14 18:30:00+07', 'COMPLETED', 'Foundation review', 'Diagnostic test, course orientation and study-plan setup.'),
    (2,  'Listening: Form Completion',           timestamptz '2026-09-16 18:30:00+07', 'COMPLETED', 'Listening foundations', 'Predict answer types and complete forms accurately.'),
    (3,  'Reading: Skimming & Scanning',          timestamptz '2026-09-21 18:30:00+07', 'COMPLETED', 'Reading foundations', 'Build skimming, scanning and keyword-location routines.'),
    (4,  'Listening: Maps & Directions',          timestamptz '2026-09-23 18:30:00+07', 'COMPLETED', 'Listening foundations', 'Follow directions and label maps under time pressure.'),
    (5,  'Reading: True / False / Not Given',     timestamptz '2026-09-28 18:30:00+07', 'COMPLETED', 'Accuracy building', 'Separate false statements from information not given.'),
    (6,  'Listening: Multiple Choice',            timestamptz '2026-09-30 18:30:00+07', 'COMPLETED', 'Accuracy building', 'Track distractors and select answers while listening.'),
    (7,  'Reading: Matching Headings',            timestamptz '2026-10-05 18:30:00+07', 'COMPLETED', 'Question strategies', 'Identify each paragraph main idea before matching headings.'),
    (8,  'Listening: Note Completion',            timestamptz '2026-10-07 18:30:00+07', 'COMPLETED', 'Question strategies', 'Use grammar and context to complete structured notes.'),
    (9,  'Reading: Sentence Completion',          timestamptz '2026-10-12 18:30:00+07', 'COMPLETED', 'Timed practice', 'Complete sentences within the stated word limit.'),
    (10, 'Listening: Section 3 Practice',         timestamptz '2026-10-14 18:30:00+07', 'SCHEDULED', 'Timed practice', 'Follow multi-speaker academic discussions and changes of opinion.'),
    (11, 'Reading: Mixed Question Practice',       timestamptz '2026-10-19 18:30:00+07', 'SCHEDULED', 'Exam rehearsal', 'Combine reading strategies in a timed passage.'),
    (12, 'Final Review & Personal Study Plan',    timestamptz '2026-10-21 18:30:00+07', 'SCHEDULED', 'Course review', 'Review progress and agree the next individual study plan.')
) as value(session_no, title, starts_at, status, phase_name, content)
on conflict (course_id, session_no) do update set
    title = excluded.title,
    starts_at = excluded.starts_at,
    ends_at = excluded.ends_at,
    zoom_url = excluded.zoom_url,
    status = excluded.status,
    phase_name = excluded.phase_name,
    content = excluded.content,
    created_by = excluded.created_by;

insert into public.attendance_sheets (
    id, session_id, status, prepared_by, locked_by, locked_at
)
select
    md5('workspace-sheet:' || session.id::text)::uuid,
    session.id,
    case when session.session_no <= 7 then 'LOCKED' else 'DRAFT' end,
    ctx.actor_id,
    case when session.session_no <= 7 then ctx.actor_id end,
    case when session.session_no <= 7 then session.ends_at + interval '15 minutes' end
from workspace_seed_context ctx
join public.course_sessions session on session.course_id = ctx.course_id
where session.session_no <= 8
on conflict (session_id) do update set
    status = excluded.status,
    prepared_by = excluded.prepared_by,
    locked_by = excluded.locked_by,
    locked_at = excluded.locked_at,
    reopened_by = null,
    reopened_at = null,
    reopen_reason = null,
    updated_at = now();

with roster as (
    select
        enrollment.student_id,
        row_number() over (order by enrollment.student_id) as student_no
    from public.enrollments enrollment
    join workspace_seed_context ctx on ctx.course_id = enrollment.course_id
    where enrollment.status <> 'WITHDRAWN'
), marked as (
    select
        session.id as session_id,
        session.session_no,
        session.starts_at,
        session.ends_at,
        roster.student_id,
        roster.student_no,
        case
            when session.session_no = 8 and roster.student_no = 5 then 'PENDING'
            when (session.session_no + roster.student_no) % 11 = 0 then 'EXCUSED'
            when (session.session_no + roster.student_no) % 7 = 0 then 'ABSENT'
            when (session.session_no + roster.student_no) % 5 = 0 then 'LEFT_EARLY'
            when (session.session_no + roster.student_no) % 4 = 0 then 'LATE'
            else 'PRESENT'
        end::public.attendance_status as attendance_status
    from workspace_seed_context ctx
    join public.course_sessions session on session.course_id = ctx.course_id and session.session_no <= 8
    cross join roster
)
insert into public.attendance_records (
    id, session_id, student_id, status, joined_at, left_at, duration_seconds,
    match_method, match_confidence, is_confirmed, confirmed_by, confirmed_at,
    adjustment_reason, updated_by
)
select
    md5('workspace-attendance:' || marked.session_id::text || ':' || marked.student_id::text)::uuid,
    marked.session_id,
    marked.student_id,
    marked.attendance_status,
    case marked.attendance_status
        when 'PRESENT' then marked.starts_at + interval '3 minutes'
        when 'LATE' then marked.starts_at + interval '18 minutes'
        when 'LEFT_EARLY' then marked.starts_at + interval '4 minutes'
    end,
    case marked.attendance_status
        when 'PRESENT' then marked.ends_at - interval '2 minutes'
        when 'LATE' then marked.ends_at - interval '2 minutes'
        when 'LEFT_EARLY' then marked.ends_at - interval '35 minutes'
    end,
    case marked.attendance_status
        when 'PRESENT' then 6900
        when 'LATE' then 6000
        when 'LEFT_EARLY' then 4800
    end,
    'manual',
    1,
    marked.session_no <= 7,
    case when marked.session_no <= 7 then ctx.actor_id end,
    case when marked.session_no <= 7 then marked.ends_at + interval '15 minutes' end,
    case marked.attendance_status
        when 'ABSENT' then 'Vang khong phep - du lieu demo.'
        when 'EXCUSED' then 'Vang co phep - du lieu demo.'
    end,
    ctx.actor_id
from marked
cross join workspace_seed_context ctx
on conflict (session_id, student_id) do update set
    status = excluded.status,
    joined_at = excluded.joined_at,
    left_at = excluded.left_at,
    duration_seconds = excluded.duration_seconds,
    match_method = excluded.match_method,
    match_confidence = excluded.match_confidence,
    is_confirmed = excluded.is_confirmed,
    confirmed_by = excluded.confirmed_by,
    confirmed_at = excluded.confirmed_at,
    adjustment_reason = excluded.adjustment_reason,
    updated_by = excluded.updated_by,
    updated_at = now();

insert into public.course_modules (
    id, program_id, title, description, skill, display_order, is_required
)
values
    ('a1000000-0000-4000-8000-000000000101', 'a1000000-0000-4000-8000-000000000001', 'Listening Foundations', 'Core listening strategies and timed practice.', 'LISTENING', 1, true),
    ('a1000000-0000-4000-8000-000000000102', 'a1000000-0000-4000-8000-000000000001', 'Reading Foundations', 'Core reading strategies and timed practice.', 'READING', 2, true)
on conflict (id) do update set
    title = excluded.title,
    description = excluded.description,
    skill = excluded.skill,
    display_order = excluded.display_order,
    is_required = excluded.is_required,
    updated_at = now();

insert into public.learning_activities (
    id, module_id, title, description, skill, activity_type, completion_method,
    display_order, is_required, default_max_score, expected_duration_minutes,
    requires_teacher_review, requires_error_analysis, requires_comprehension_rating,
    created_by
)
select
    value.id,
    value.module_id,
    value.title,
    value.description,
    value.skill::public.skill_type,
    'ONLINE_TEST'::public.activity_type,
    'WEB_OR_MANUAL'::public.completion_method,
    value.display_order,
    true,
    40,
    25,
    false,
    true,
    true,
    ctx.actor_id
from workspace_seed_context ctx
cross join (values
    ('a1000000-0000-4000-8000-000000001001'::uuid, 'a1000000-0000-4000-8000-000000000101'::uuid, 'Listening Diagnostic: Form Completion', 'Baseline form-completion practice.', 'LISTENING', 1),
    ('a1000000-0000-4000-8000-000000001002'::uuid, 'a1000000-0000-4000-8000-000000000102'::uuid, 'Reading Diagnostic: Matching Headings', 'Baseline matching-headings practice.', 'READING', 1),
    ('a1000000-0000-4000-8000-000000001003'::uuid, 'a1000000-0000-4000-8000-000000000101'::uuid, 'Listening: Note Completion Sprint', 'Timed note-completion exercise.', 'LISTENING', 2),
    ('a1000000-0000-4000-8000-000000001004'::uuid, 'a1000000-0000-4000-8000-000000000102'::uuid, 'Reading: True / False / Not Given', 'Evidence-location accuracy drill.', 'READING', 2),
    ('a1000000-0000-4000-8000-000000001005'::uuid, 'a1000000-0000-4000-8000-000000000101'::uuid, 'Listening: Map Labelling', 'Directions and map-language practice.', 'LISTENING', 3),
    ('a1000000-0000-4000-8000-000000001006'::uuid, 'a1000000-0000-4000-8000-000000000102'::uuid, 'Reading: Sentence Completion', 'Word-limit and grammar accuracy practice.', 'READING', 3),
    ('a1000000-0000-4000-8000-000000001007'::uuid, 'a1000000-0000-4000-8000-000000000101'::uuid, 'Listening: Multiple Choice', 'Distractor recognition under time pressure.', 'LISTENING', 4),
    ('a1000000-0000-4000-8000-000000001008'::uuid, 'a1000000-0000-4000-8000-000000000102'::uuid, 'Reading: Summary Completion', 'Context and paraphrase recognition practice.', 'READING', 4)
) as value(id, module_id, title, description, skill, display_order)
on conflict (id) do update set
    title = excluded.title,
    description = excluded.description,
    skill = excluded.skill,
    activity_type = excluded.activity_type,
    completion_method = excluded.completion_method,
    display_order = excluded.display_order,
    is_required = excluded.is_required,
    default_max_score = excluded.default_max_score,
    expected_duration_minutes = excluded.expected_duration_minutes,
    requires_error_analysis = excluded.requires_error_analysis,
    requires_comprehension_rating = excluded.requires_comprehension_rating,
    updated_at = now();

insert into public.course_activities (
    id, course_id, session_id, activity_id, opens_at, due_at,
    is_required_override, max_score_override, instructions_override,
    is_published, created_by
)
select
    value.id,
    ctx.course_id,
    session.id,
    value.activity_id,
    session.starts_at - interval '1 day',
    session.ends_at + interval '1 day',
    true,
    40,
    'Hoan thanh bai va ghi lai loi sai de trao doi trong buoi hoc tiep theo.',
    true,
    ctx.actor_id
from workspace_seed_context ctx
join public.course_sessions session on session.course_id = ctx.course_id
join (values
    ('b1000000-0000-4000-8000-000000001001'::uuid, 1, 'a1000000-0000-4000-8000-000000001001'::uuid),
    ('b1000000-0000-4000-8000-000000001002'::uuid, 1, 'a1000000-0000-4000-8000-000000001002'::uuid),
    ('b1000000-0000-4000-8000-000000001003'::uuid, 2, 'a1000000-0000-4000-8000-000000001003'::uuid),
    ('b1000000-0000-4000-8000-000000001004'::uuid, 2, 'a1000000-0000-4000-8000-000000001004'::uuid),
    ('b1000000-0000-4000-8000-000000001005'::uuid, 3, 'a1000000-0000-4000-8000-000000001005'::uuid),
    ('b1000000-0000-4000-8000-000000001006'::uuid, 3, 'a1000000-0000-4000-8000-000000001006'::uuid),
    ('b1000000-0000-4000-8000-000000001007'::uuid, 4, 'a1000000-0000-4000-8000-000000001007'::uuid),
    ('b1000000-0000-4000-8000-000000001008'::uuid, 4, 'a1000000-0000-4000-8000-000000001008'::uuid)
) as value(id, session_no, activity_id) on value.session_no = session.session_no
on conflict (id) do update set
    session_id = excluded.session_id,
    activity_id = excluded.activity_id,
    opens_at = excluded.opens_at,
    due_at = excluded.due_at,
    is_required_override = excluded.is_required_override,
    max_score_override = excluded.max_score_override,
    instructions_override = excluded.instructions_override,
    is_published = true,
    created_by = excluded.created_by,
    updated_at = now();

with roster as (
    select
        enrollment.student_id,
        row_number() over (order by enrollment.student_id) as student_no
    from public.enrollments enrollment
    join workspace_seed_context ctx on ctx.course_id = enrollment.course_id
    where enrollment.status <> 'WITHDRAWN'
), attempts as (
    select
        activity.id as course_activity_id,
        activity.due_at,
        roster.student_id,
        roster.student_no,
        case roster.student_no when 1 then 35 when 2 then 30 when 3 then 24 else 18 end as score,
        case roster.student_no when 1 then 90 when 2 then 78 when 3 then 61 else 48 end as comprehension,
        case roster.student_no
            when 1 then 'Minor spelling errors; answer prediction is consistent.'
            when 2 then 'Missed paraphrases in two questions.'
            when 3 then 'Loses focus around distractors and changes answers too early.'
            else 'Needs support with vocabulary, timing and locating evidence.'
        end as error_analysis,
        case roster.student_no
            when 1 then 'Maintain timed practice twice per week.'
            when 2 then 'Build a paraphrase notebook after each exercise.'
            when 3 then 'Review distractor patterns and complete one focused drill.'
            else 'Redo the exercise untimed with teacher guidance.'
        end as improvement_plan
    from public.course_activities activity
    join workspace_seed_context ctx on ctx.course_id = activity.course_id
    cross join roster
    where activity.id between 'b1000000-0000-4000-8000-000000001001'::uuid
                          and 'b1000000-0000-4000-8000-000000001008'::uuid
      and roster.student_no <= 4
)
insert into public.student_activity_attempts (
    id, class_activity_id, student_id, attempt_no, source, status, review_status,
    score, max_score, correct_count, incorrect_count, unanswered_count,
    duration_seconds, comprehension_percent, error_analysis, improvement_plan,
    started_at, submitted_at, completed_at, is_late, metadata
)
select
    md5('workspace-attempt:' || course_activity_id::text || ':' || student_id::text)::uuid,
    course_activity_id,
    student_id,
    1,
    'TEACHER_MANUAL',
    'COMPLETED',
    'VERIFIED',
    score,
    40,
    score,
    40 - score,
    0,
    1260 + student_no * 240,
    comprehension,
    error_analysis,
    improvement_plan,
    due_at - interval '45 minutes',
    due_at - interval '15 minutes',
    due_at - interval '15 minutes',
    false,
    jsonb_build_object('seed', 'course_workspace_demo')
from attempts
on conflict (class_activity_id, student_id, attempt_no) do update set
    source = excluded.source,
    status = excluded.status,
    review_status = excluded.review_status,
    score = excluded.score,
    max_score = excluded.max_score,
    correct_count = excluded.correct_count,
    incorrect_count = excluded.incorrect_count,
    unanswered_count = excluded.unanswered_count,
    duration_seconds = excluded.duration_seconds,
    comprehension_percent = excluded.comprehension_percent,
    error_analysis = excluded.error_analysis,
    improvement_plan = excluded.improvement_plan,
    started_at = excluded.started_at,
    submitted_at = excluded.submitted_at,
    completed_at = excluded.completed_at,
    is_late = excluded.is_late,
    metadata = excluded.metadata,
    updated_at = now();

commit;
