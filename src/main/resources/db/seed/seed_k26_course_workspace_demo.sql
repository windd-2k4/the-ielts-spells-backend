-- Idempotent workspace data for course 6347839e-6c4f-49db-a63a-de654bba1855.
-- Reuses active student accounts; it never creates or changes users.

begin;

create temporary table k26_seed_context (
    course_id uuid primary key,
    actor_id uuid not null
) on commit drop;

insert into k26_seed_context (course_id, actor_id)
select c.id, coalesce(c.created_by, (
    select p.id
    from public.profiles p
    join public.user_roles r on r.user_id = p.id
    where p.is_active = true and r.role = 'ADMIN'
    order by p.created_at
    limit 1
))
from public.courses c
where c.id = '6347839e-6c4f-49db-a63a-de654bba1855';

do $$
begin
    if not exists (select 1 from k26_seed_context) then
        raise exception 'Khong tim thay khoa hoc 6347839e-6c4f-49db-a63a-de654bba1855.';
    end if;
    if exists (select 1 from k26_seed_context where actor_id is null) then
        raise exception 'Khong tim thay tai khoan quan tri de gan du lieu demo.';
    end if;
    if not exists (
        select 1
        from public.student_profiles sp
        join public.profiles p on p.id = sp.user_id
        where p.is_active = true
    ) then
        raise exception 'Khong co hoc vien dang hoat dong de ghi danh.';
    end if;
end
$$;

insert into public.course_programs (
    id, code, name, description, level, target_band, total_sessions,
    is_public, is_active, created_by
)
select
    md5('k26-speaking-writing-program')::uuid,
    'SEED-PROGRAM-SW-K26',
    'IELTS Intensive Speaking & Writing',
    'Chuong trinh demo cho workspace khoa IELTS Intensive Speaking & Writing - K26.',
    'Intermediate', 6.5, 20, false, true, actor_id
from k26_seed_context
on conflict (code) do update set
    name = excluded.name,
    description = excluded.description,
    level = excluded.level,
    target_band = excluded.target_band,
    total_sessions = excluded.total_sessions,
    is_active = true,
    updated_at = now();

update public.courses
set program_id = (select id from public.course_programs where code = 'SEED-PROGRAM-SW-K26'),
    skill_pair = 'SPEAKING_WRITING',
    level = 'Intermediate',
    target_band = 6.5,
    total_sessions = 20,
    ends_on = date '2026-11-30',
    updated_at = now()
where id = (select course_id from k26_seed_context);

with active_students as (
    select
        sp.user_id as student_id,
        row_number() over (order by sp.student_code, sp.user_id) as student_no
    from public.student_profiles sp
    join public.profiles p on p.id = sp.user_id
    where p.is_active = true
)
insert into public.enrollments (
    id, course_id, student_id, status, enrolled_at, started_on, ended_on,
    notes, planned_exam_month, exam_registration_status, target_note, attendance_note
)
select
    md5('k26-enrollment:' || student.student_id::text)::uuid,
    ctx.course_id,
    student.student_id,
    'ACTIVE',
    timestamptz '2026-09-22 09:00:00+07',
    date '2026-09-24',
    null,
    'Ghi danh boi seed workspace K26.',
    (date '2027-01-01' + ((student.student_no - 1) % 3) * interval '1 month')::date,
    case when student.student_no % 3 = 0 then 'REGISTERED' else 'NOT_REGISTERED' end,
    case
        when student.student_no % 3 = 0 then 'Muc tieu 7.0; uu tien Writing Task 2.'
        when student.student_no % 3 = 1 then 'Muc tieu 6.5; can tang do troi chay Speaking.'
        else 'Muc tieu 6.5; can cai thien tinh mach lac.'
    end,
    case when student.student_no = 4 then 'Can theo doi chuyen can trong 2 tuan tiep theo.' end
from k26_seed_context ctx
cross join active_students student
on conflict (course_id, student_id) do update set
    status = 'ACTIVE',
    started_on = excluded.started_on,
    ended_on = null,
    notes = excluded.notes,
    planned_exam_month = excluded.planned_exam_month,
    exam_registration_status = excluded.exam_registration_status,
    target_note = excluded.target_note,
    attendance_note = excluded.attendance_note;

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
    'https://zoom.us/j/987654222',
    value.status::public.session_status,
    value.phase_name,
    value.content,
    ctx.actor_id
from k26_seed_context ctx
cross join (values
    (1,  'Diagnostic Speaking & Writing',          timestamptz '2026-09-24 18:30:00+07', 'COMPLETED', 'Khoi dong',       'Danh gia dau vao va lap ke hoach hoc tap ca nhan.'),
    (2,  'Writing Task 1: Overview & Key Features', timestamptz '2026-09-28 18:30:00+07', 'COMPLETED', 'Nen tang',        'Viet overview va chon dac diem noi bat.'),
    (3,  'Speaking Part 1: Fluency Foundations',    timestamptz '2026-10-01 18:30:00+07', 'COMPLETED', 'Nen tang',        'Mo rong cau tra loi tu nhien va ro rang.'),
    (4,  'Writing Task 2: Essay Structure',         timestamptz '2026-10-05 18:30:00+07', 'COMPLETED', 'Phat trien y',    'Xay dung thesis, topic sentence va bo cuc bai.'),
    (5,  'Speaking Part 2: Storytelling',           timestamptz '2026-10-08 18:30:00+07', 'COMPLETED', 'Phat trien y',    'Lap dan y nhanh va noi lien tuc trong hai phut.'),
    (6,  'Writing Task 2: Opinion Essays',          timestamptz '2026-10-12 18:30:00+07', 'COMPLETED', 'Lap luan',         'Bao ve quan diem bang ly do va vi du phu hop.'),
    (7,  'Speaking Part 3: Discussion Skills',      timestamptz '2026-10-15 18:30:00+07', 'COMPLETED', 'Lap luan',         'Phan tich, so sanh va dua ra du doan.'),
    (8,  'Writing: Coherence & Cohesion',           timestamptz '2026-10-19 18:30:00+07', 'COMPLETED', 'Tinh mach lac',    'Lien ket y tuong ma khong lam dung tu noi.'),
    (9,  'Speaking Mock Test 1',                    timestamptz '2026-10-22 18:30:00+07', 'COMPLETED', 'Luyen de',         'Thi thu day du ba phan va nhan phan hoi.'),
    (10, 'Writing Mock Test 1',                     timestamptz '2026-10-26 18:30:00+07', 'SCHEDULED', 'Luyen de',         'Hoan thanh Task 1 va Task 2 trong thoi gian gioi han.'),
    (11, 'Speaking: Pronunciation Clinic',          timestamptz '2026-10-29 18:30:00+07', 'SCHEDULED', 'Nang diem',        'Trong am, noi am va ngu dieu trong cau dai.'),
    (12, 'Writing Task 1: Process & Maps',          timestamptz '2026-11-02 18:30:00+07', 'SCHEDULED', 'Nang diem',        'Mo ta quy trinh va thay doi tren ban do.'),
    (13, 'Speaking: Lexical Resource',              timestamptz '2026-11-05 18:30:00+07', 'SCHEDULED', 'Nang diem',        'Dung cum tu tu nhien va paraphrase linh hoat.'),
    (14, 'Writing Task 2: Problem & Solution',      timestamptz '2026-11-09 18:30:00+07', 'SCHEDULED', 'Nang diem',        'Phan tich nguyen nhan va de xuat giai phap.'),
    (15, 'Speaking Mock Test 2',                    timestamptz '2026-11-12 18:30:00+07', 'SCHEDULED', 'Luyen de',         'Thi thu va danh gia theo bon tieu chi IELTS.'),
    (16, 'Writing Mock Test 2',                     timestamptz '2026-11-16 18:30:00+07', 'SCHEDULED', 'Luyen de',         'Luyen de day du va chua bai co trong tam.'),
    (17, 'Speaking: Band 7 Response Lab',           timestamptz '2026-11-19 18:30:00+07', 'SCHEDULED', 'Ve dich',          'Nang cap cau tra loi bang phan tich va vi du.'),
    (18, 'Writing: Band 7 Editing Lab',             timestamptz '2026-11-23 18:30:00+07', 'SCHEDULED', 'Ve dich',          'Tu bien tap bai viet theo checklist band 7.'),
    (19, 'Final Speaking Assessment',               timestamptz '2026-11-26 18:30:00+07', 'SCHEDULED', 'Danh gia cuoi khoa','Danh gia Speaking cuoi khoa va phan hoi ca nhan.'),
    (20, 'Final Writing Assessment & Study Plan',   timestamptz '2026-11-30 18:30:00+07', 'SCHEDULED', 'Danh gia cuoi khoa','Danh gia Writing va thong nhat ke hoach tiep theo.')
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
    md5('k26-attendance-sheet:' || session.id::text)::uuid,
    session.id,
    case when session.session_no <= 8 then 'LOCKED' else 'DRAFT' end,
    ctx.actor_id,
    case when session.session_no <= 8 then ctx.actor_id end,
    case when session.session_no <= 8 then session.ends_at + interval '15 minutes' end
from k26_seed_context ctx
join public.course_sessions session on session.course_id = ctx.course_id
where session.session_no <= 9
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
    join k26_seed_context ctx on ctx.course_id = enrollment.course_id
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
            when session.session_no = 9 and roster.student_no = 7 then 'PENDING'
            when (session.session_no + roster.student_no) % 13 = 0 then 'EXCUSED'
            when (session.session_no + roster.student_no) % 9 = 0 then 'ABSENT'
            when (session.session_no + roster.student_no) % 7 = 0 then 'LEFT_EARLY'
            when (session.session_no + roster.student_no) % 5 = 0 then 'LATE'
            else 'PRESENT'
        end::public.attendance_status as attendance_status
    from k26_seed_context ctx
    join public.course_sessions session on session.course_id = ctx.course_id and session.session_no <= 9
    cross join roster
)
insert into public.attendance_records (
    id, session_id, student_id, status, joined_at, left_at, duration_seconds,
    match_method, match_confidence, is_confirmed, confirmed_by, confirmed_at,
    adjustment_reason, updated_by, interaction_count
)
select
    md5('k26-attendance:' || marked.session_id::text || ':' || marked.student_id::text)::uuid,
    marked.session_id,
    marked.student_id,
    marked.attendance_status,
    case marked.attendance_status
        when 'PRESENT' then marked.starts_at + interval '3 minutes'
        when 'LATE' then marked.starts_at + interval '17 minutes'
        when 'LEFT_EARLY' then marked.starts_at + interval '4 minutes'
    end,
    case marked.attendance_status
        when 'PRESENT' then marked.ends_at - interval '2 minutes'
        when 'LATE' then marked.ends_at - interval '2 minutes'
        when 'LEFT_EARLY' then marked.ends_at - interval '32 minutes'
    end,
    case marked.attendance_status
        when 'PRESENT' then 6900
        when 'LATE' then 6060
        when 'LEFT_EARLY' then 5040
    end,
    'manual',
    1,
    marked.session_no <= 8,
    case when marked.session_no <= 8 then ctx.actor_id end,
    case when marked.session_no <= 8 then marked.ends_at + interval '15 minutes' end,
    case marked.attendance_status
        when 'ABSENT' then 'Vang khong phep - du lieu demo.'
        when 'EXCUSED' then 'Vang co phep - du lieu demo.'
        when 'LATE' then 'Den muon - du lieu demo.'
    end,
    ctx.actor_id,
    ((marked.session_no + marked.student_no) % 5)::integer
from marked
cross join k26_seed_context ctx
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
    interaction_count = excluded.interaction_count,
    updated_at = now();

insert into public.course_modules (
    id, program_id, title, description, skill, display_order, is_required
)
values
    (md5('k26-speaking-module')::uuid, md5('k26-speaking-writing-program')::uuid,
     'Speaking Intensive', 'Fluency, pronunciation, vocabulary and idea development.', 'SPEAKING', 1, true),
    (md5('k26-writing-module')::uuid, md5('k26-speaking-writing-program')::uuid,
     'Writing Intensive', 'Task achievement, coherence, vocabulary and grammar.', 'WRITING', 2, true)
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
    md5('k26-learning-activity:' || value.session_no)::uuid,
    case value.skill when 'SPEAKING' then md5('k26-speaking-module')::uuid else md5('k26-writing-module')::uuid end,
    value.title,
    value.description,
    value.skill::public.skill_type,
    case value.skill when 'SPEAKING' then 'AUDIO_SUBMISSION' else 'WRITING_SUBMISSION' end::public.activity_type,
    'WEB_OR_MANUAL'::public.completion_method,
    value.skill_order,
    true,
    100,
    case value.skill when 'SPEAKING' then 15 else 60 end,
    true,
    true,
    true,
    ctx.actor_id
from k26_seed_context ctx
cross join (values
    (1,  1, 'Speaking', 'Speaking Diagnostic',            'Record a complete mock interview.', 'SPEAKING'),
    (2,  1, 'Writing',  'Task 1 Overview Practice',       'Write an overview and select key features.', 'WRITING'),
    (3,  2, 'Speaking', 'Part 1 Fluency Drill',           'Answer familiar-topic questions with developed ideas.', 'SPEAKING'),
    (4,  2, 'Writing',  'Task 2 Structure Practice',      'Plan and write a four-paragraph essay.', 'WRITING'),
    (5,  3, 'Speaking', 'Part 2 Storytelling Recording',  'Record a two-minute cue-card response.', 'SPEAKING'),
    (6,  3, 'Writing',  'Opinion Essay Practice',         'Write and support a clear position.', 'WRITING'),
    (7,  4, 'Speaking', 'Part 3 Discussion Recording',    'Develop analytical answers with examples.', 'SPEAKING'),
    (8,  4, 'Writing',  'Coherence Editing Practice',     'Revise paragraph flow and referencing.', 'WRITING'),
    (9,  5, 'Speaking', 'Speaking Mock Test 1',           'Complete all three speaking parts.', 'SPEAKING')
) as value(session_no, skill_order, skill_key, title, description, skill)
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
    requires_teacher_review = excluded.requires_teacher_review,
    requires_error_analysis = excluded.requires_error_analysis,
    requires_comprehension_rating = excluded.requires_comprehension_rating,
    updated_at = now();

insert into public.course_activities (
    id, course_id, session_id, activity_id, opens_at, due_at,
    is_required_override, max_score_override, instructions_override,
    is_published, created_by
)
select
    md5('k26-course-activity:' || session.session_no)::uuid,
    ctx.course_id,
    session.id,
    md5('k26-learning-activity:' || session.session_no)::uuid,
    session.starts_at - interval '1 day',
    session.ends_at + interval '2 days',
    true,
    100,
    'Hoan thanh bai va xem phan hoi chi tiet truoc buoi hoc tiep theo.',
    true,
    ctx.actor_id
from k26_seed_context ctx
join public.course_sessions session on session.course_id = ctx.course_id
where session.session_no <= 9
on conflict (id) do update set
    course_id = excluded.course_id,
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
    join k26_seed_context ctx on ctx.course_id = enrollment.course_id
    where enrollment.status <> 'WITHDRAWN'
), attempts as (
    select
        activity.id as course_activity_id,
        activity.due_at,
        session.session_no,
        roster.student_id,
        roster.student_no,
        (52 + roster.student_no * 6 + ((session.session_no % 3) - 1))::smallint as result_percent
    from public.course_activities activity
    join k26_seed_context ctx on ctx.course_id = activity.course_id
    join public.course_sessions session on session.id = activity.session_id
    cross join roster
    where session.session_no <= 9
)
insert into public.student_activity_attempts (
    id, class_activity_id, student_id, attempt_no, source, status, review_status,
    score, max_score, duration_seconds, comprehension_percent,
    error_analysis, improvement_plan, reflection,
    started_at, submitted_at, completed_at, is_late, metadata
)
select
    md5('k26-attempt:' || course_activity_id::text || ':' || student_id::text)::uuid,
    course_activity_id,
    student_id,
    1,
    'TEACHER_MANUAL',
    'COMPLETED',
    'VERIFIED',
    result_percent,
    100,
    900 + student_no * 180,
    result_percent,
    case
        when result_percent < 65 then 'Can cai thien do chinh xac, tu vung va kha nang phat trien y.'
        when result_percent < 80 then 'Da co nen tang tot; can giam loi lap tu va tang tinh mach lac.'
        else 'The hien on dinh; chi con mot so loi nho ve dien dat.'
    end,
    case
        when result_percent < 65 then 'On lai bai mau va hoan thanh hai bai luyen co huong dan moi tuan.'
        when result_percent < 80 then 'Ghi lai loi sai va tu bien tap bai truoc khi nop.'
        else 'Duy tri mot bai luyen gioi han thoi gian moi tuan.'
    end,
    'Du lieu demo de kiem thu tien do hoc tap.',
    due_at - interval '3 hours',
    due_at - interval '90 minutes',
    due_at - interval '90 minutes',
    false,
    jsonb_build_object('seed', 'k26_course_workspace_demo', 'sessionNo', session_no)
from attempts
on conflict (class_activity_id, student_id, attempt_no) do update set
    source = excluded.source,
    status = excluded.status,
    review_status = excluded.review_status,
    score = excluded.score,
    max_score = excluded.max_score,
    duration_seconds = excluded.duration_seconds,
    comprehension_percent = excluded.comprehension_percent,
    error_analysis = excluded.error_analysis,
    improvement_plan = excluded.improvement_plan,
    reflection = excluded.reflection,
    started_at = excluded.started_at,
    submitted_at = excluded.submitted_at,
    completed_at = excluded.completed_at,
    is_late = excluded.is_late,
    metadata = excluded.metadata,
    updated_at = now();

do $$
declare
    seeded_course_id constant uuid := '6347839e-6c4f-49db-a63a-de654bba1855';
begin
    if (select count(*) from public.enrollments where course_id = seeded_course_id and status = 'ACTIVE') = 0 then
        raise exception 'Seed khong tao duoc ghi danh.';
    end if;
    if (select count(*) from public.course_sessions where course_id = seeded_course_id) <> 20 then
        raise exception 'Seed phai co dung 20 buoi hoc.';
    end if;
    if (select count(*) from public.attendance_records ar join public.course_sessions cs on cs.id = ar.session_id where cs.course_id = seeded_course_id) = 0 then
        raise exception 'Seed khong tao duoc du lieu diem danh.';
    end if;
    if (select count(*) from public.student_activity_attempts saa join public.course_activities ca on ca.id = saa.class_activity_id where ca.course_id = seeded_course_id) = 0 then
        raise exception 'Seed khong tao duoc du lieu tien do.';
    end if;
end
$$;

commit;
