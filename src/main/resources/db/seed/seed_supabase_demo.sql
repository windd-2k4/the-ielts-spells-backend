-- Demo seed for an existing Supabase administrator.
-- Prerequisite: Flyway migrations have been applied and at least one active
-- public.profiles user has public.user_roles.role = 'ADMIN'.
-- This script never creates or changes auth.users, profiles, staff, roles,
-- student profiles, enrollments, or attendance records.

begin;

create temporary table seed_context (
    admin_id uuid primary key
) on commit drop;

insert into seed_context (admin_id)
select p.id
from public.profiles p
join public.user_roles r on r.user_id = p.id
where r.role = 'ADMIN'
  and p.is_active = true
order by p.created_at
limit 1;

do $$
begin
    if not exists (select 1 from seed_context) then
        raise exception
            'Khong tim thay tai khoan ADMIN dang hoat dong trong public.profiles/public.user_roles. Hay dang nhap bang tai khoan admin hien co hoac gan role ADMIN truoc khi chay seed.';
    end if;
end
$$;

insert into public.courses (
    code, name, description, level, skill_pair, target_band, total_sessions,
    tuition_amount, capacity, starts_on, ends_on, status, default_zoom_url,
    is_public, is_active, created_by
)
values
    (
        'SEED-LR-2609',
        'IELTS Listening & Reading Foundation',
        'Lop mau de kiem tra quy trinh quan ly khoa hoc, lich hoc va hoc lieu.',
        'Foundation', 'LISTENING_READING', '5.5', 12, 1800000, 24,
        date '2026-09-14', date '2026-10-23', 'ACTIVE',
        'https://zoom.us/j/1234567890', true, true,
        (select admin_id from seed_context)
    ),
    (
        'SEED-SW-2609',
        'IELTS Speaking & Writing Intensive',
        'Lop mau dang tuyen de kiem tra trang khoa hoc va lead tuyen sinh.',
        'Intermediate', 'SPEAKING_WRITING', '6.5', 16, 2800000, 18,
        date '2026-10-05', date '2026-11-27', 'OPEN',
        'https://zoom.us/j/9876543210', true, true,
        (select admin_id from seed_context)
    )
on conflict (code) do update set
    name = excluded.name,
    description = excluded.description,
    level = excluded.level,
    skill_pair = excluded.skill_pair,
    target_band = excluded.target_band,
    total_sessions = excluded.total_sessions,
    tuition_amount = excluded.tuition_amount,
    capacity = excluded.capacity,
    starts_on = excluded.starts_on,
    ends_on = excluded.ends_on,
    status = excluded.status,
    default_zoom_url = excluded.default_zoom_url,
    is_public = excluded.is_public,
    is_active = excluded.is_active,
    created_by = excluded.created_by,
    updated_at = now();

insert into public.course_sessions (
    course_id, session_no, title, starts_at, ends_at, zoom_url, status,
    phase_name, content, created_by
)
select
    c.id,
    session_data.session_no,
    session_data.title,
    session_data.starts_at,
    session_data.ends_at,
    c.default_zoom_url,
    session_data.status::public.session_status,
    session_data.phase_name,
    session_data.content,
    seed_context.admin_id
from (
    values
        ('SEED-LR-2609', 1, 'Diagnostic Listening and Reading', timestamptz '2026-09-14 18:30:00+07', timestamptz '2026-09-14 20:30:00+07', 'COMPLETED', 'Khoi dong', 'Lam bai dau vao va gioi thieu lo trinh hoc.'),
        ('SEED-LR-2609', 2, 'Listening: Form Completion', timestamptz '2026-09-16 18:30:00+07', timestamptz '2026-09-16 20:30:00+07', 'SCHEDULED', 'Ky nang nghe', 'Luyen nghe dang form completion va cach du doan dap an.'),
        ('SEED-SW-2609', 1, 'Speaking Part 1 Fluency', timestamptz '2026-10-05 18:30:00+07', timestamptz '2026-10-05 20:30:00+07', 'SCHEDULED', 'Speaking', 'Xay dung cau tra loi tu nhien cho Speaking Part 1.'),
        ('SEED-SW-2609', 2, 'Writing Task 2: Opinion Essay', timestamptz '2026-10-07 18:30:00+07', timestamptz '2026-10-07 20:30:00+07', 'SCHEDULED', 'Writing', 'Lap dan y va viet bai opinion essay co cau truc ro rang.')
) as session_data (course_code, session_no, title, starts_at, ends_at, status, phase_name, content)
join public.courses c on c.code = session_data.course_code
cross join seed_context
on conflict (course_id, session_no) do update set
    title = excluded.title,
    starts_at = excluded.starts_at,
    ends_at = excluded.ends_at,
    zoom_url = excluded.zoom_url,
    status = excluded.status,
    phase_name = excluded.phase_name,
    content = excluded.content;

insert into public.learning_resources (
    code, title, description, skill, category, resource_type, scope, course_id,
    external_url, teacher_only, status, created_by
)
select
    resource_data.code,
    resource_data.title,
    resource_data.description,
    resource_data.skill::public.skill_type,
    resource_data.category,
    resource_data.resource_type,
    resource_data.scope,
    c.id,
    resource_data.external_url,
    resource_data.teacher_only,
    resource_data.status,
    seed_context.admin_id
from (
    values
        ('RES-SEED-READ-001', 'Reading skimming checklist', 'Checklist mau de luyen ky nang skim nhanh truoc khi lam bai.', 'READING', 'Strategy', 'DRIVE_LINK', 'GLOBAL', null::text, 'https://ielts.org/', false, 'PUBLISHED'),
        ('RES-SEED-WRITE-001', 'Writing Task 2 teacher notes', 'Ghi chu noi bo cho buoi Writing Task 2 cua lop mau.', 'WRITING', 'Lesson note', 'DRIVE_LINK', 'COURSE', 'SEED-SW-2609', 'https://ielts.org/for-test-takers/test-format', true, 'DRAFT')
) as resource_data (code, title, description, skill, category, resource_type, scope, course_code, external_url, teacher_only, status)
left join public.courses c on c.code = resource_data.course_code
cross join seed_context
on conflict (code) do update set
    title = excluded.title,
    description = excluded.description,
    skill = excluded.skill,
    category = excluded.category,
    resource_type = excluded.resource_type,
    scope = excluded.scope,
    course_id = excluded.course_id,
    external_url = excluded.external_url,
    teacher_only = excluded.teacher_only,
    status = excluded.status,
    created_by = excluded.created_by,
    updated_at = now();

insert into public.exercise_templates (
    code, title, instructions, skill, category, exercise_type, completion_mode,
    scope, duration_minutes, max_score, attempt_limit, requires_teacher_review,
    content, answer_key, status, created_by
)
values (
    'EX-SEED-LR-001',
    'Reading practice: remote work',
    'Bai tap doc hieu ngan dung de kiem tra luong tao va xem bai tap.',
    'READING',
    'Reading practice',
    'MULTIPLE_CHOICE',
    'WEB',
    'GLOBAL',
    10,
    2,
    3,
    false,
    '{"passage":"Remote work gives employees flexibility, but successful teams still need clear routines and communication.","question":"What do successful remote teams need?","options":["Clear routines and communication","More office space","Fewer meetings","A longer commute"]}'::jsonb,
    '{"correctOptionIndex":0,"explanation":"Doan van neu ro successful teams need clear routines and communication."}'::jsonb,
    'PUBLISHED',
    (select admin_id from seed_context)
)
on conflict (code) do update set
    title = excluded.title,
    instructions = excluded.instructions,
    skill = excluded.skill,
    category = excluded.category,
    exercise_type = excluded.exercise_type,
    completion_mode = excluded.completion_mode,
    scope = excluded.scope,
    course_id = excluded.course_id,
    source_url = excluded.source_url,
    duration_minutes = excluded.duration_minutes,
    max_score = excluded.max_score,
    attempt_limit = excluded.attempt_limit,
    requires_teacher_review = excluded.requires_teacher_review,
    status = excluded.status,
    content = excluded.content,
    answer_key = excluded.answer_key,
    created_by = excluded.created_by,
    updated_at = now();

insert into public.tests (
    code, title, description, duration_minutes, status, created_by, primary_skill,
    test_type, version, tags, builder_content
)
values (
    'TST-SEED-R-001',
    'Reading mini test: Remote work',
    'De doc hieu mau o trang thai nhap de kiem tra Test Builder.',
    15,
    'DRAFT',
    (select admin_id from seed_context),
    'READING',
    'SINGLE_SKILL',
    'v1.0',
    '["seed", "reading", "practice"]'::jsonb,
    '{
        "format":"PRACTICE",
        "passages":[{
            "id":"seed-reading-passage-1",
            "passageNo":1,
            "title":"Remote work and productivity",
            "content":"<p>Remote work gives employees flexibility, but successful teams still need clear routines and communication.</p>",
            "questionGroups":[{
                "id":"seed-reading-group-1",
                "title":"Questions 1-2",
                "typeFormat":"MULTIPLE_CHOICE",
                "instructions":"Choose the correct letter, A, B, C or D.",
                "answerSource":"OPTION_BANK",
                "questions":[
                    {
                        "id":"seed-reading-question-1",
                        "number":1,
                        "typeFormat":"MULTIPLE_CHOICE",
                        "prompt":"What is one benefit of remote work mentioned in the passage?",
                        "options":[
                            {"id":"seed-option-a","text":"Flexibility"},
                            {"id":"seed-option-b","text":"A longer commute"},
                            {"id":"seed-option-c","text":"More office space"},
                            {"id":"seed-option-d","text":"Less communication"}
                        ],
                        "correctAnswers":["seed-option-a"],
                        "isComplete":true,
                        "hasError":false
                    },
                    {
                        "id":"seed-reading-question-2",
                        "number":2,
                        "typeFormat":"MULTIPLE_CHOICE",
                        "prompt":"What do successful remote teams still need?",
                        "options":[
                            {"id":"seed-option-e","text":"Clear routines and communication"},
                            {"id":"seed-option-f","text":"A new office"},
                            {"id":"seed-option-g","text":"No schedules"},
                            {"id":"seed-option-h","text":"Fewer employees"}
                        ],
                        "correctAnswers":["seed-option-e"],
                        "isComplete":true,
                        "hasError":false
                    }
                ]
            }]
        }]
    }'::jsonb
)
on conflict (code) do update set
    title = excluded.title,
    description = excluded.description,
    duration_minutes = excluded.duration_minutes,
    status = excluded.status,
    created_by = excluded.created_by,
    primary_skill = excluded.primary_skill,
    test_type = excluded.test_type,
    version = excluded.version,
    tags = excluded.tags,
    builder_content = excluded.builder_content,
    current_published_version_id = null,
    draft_revision = public.tests.draft_revision + 1,
    updated_at = now();

insert into public.leads (
    id, full_name, phone, email, current_band, target_band, interested_course_id,
    source, status, assigned_to
)
select
    lead_data.id,
    lead_data.full_name,
    lead_data.phone,
    lead_data.email,
    lead_data.current_band::numeric(2,1),
    lead_data.target_band::numeric(2,1),
    null::uuid,
    lead_data.source,
    lead_data.status::public.lead_status,
    seed_context.admin_id
from (
    values
        ('95c4d36e-9c55-43d5-bfbf-183b6f721001'::uuid, 'Nguyen Minh Anh', '0901000001', 'minh.anh.demo@example.com', '4.5', '5.5', 'Website', 'NEW'),
        ('95c4d36e-9c55-43d5-bfbf-183b6f721002'::uuid, 'Tran Gia Han', '0901000002', 'gia.han.demo@example.com', '5.5', '6.5', 'Facebook', 'CONTACTED')
) as lead_data (id, full_name, phone, email, current_band, target_band, source, status)
cross join seed_context
on conflict (id) do update set
    full_name = excluded.full_name,
    phone = excluded.phone,
    email = excluded.email,
    current_band = excluded.current_band,
    target_band = excluded.target_band,
    interested_course_id = excluded.interested_course_id,
    source = excluded.source,
    status = excluded.status,
    assigned_to = excluded.assigned_to,
    updated_at = now();

commit;
