-- IELTS Center Management System - PostgreSQL / Supabase schema
-- Scope: CMS, admissions, courses/classes, assignments, tests, Zoom attendance,
-- Writing AI, dashboards/notifications and auditability.

-- Local PostgreSQL compatibility. Supabase already provides these roles and auth objects;
-- the guards make this block a no-op there.
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then
    create role anon nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then
    create role authenticated nologin;
  end if;
end
$$;

create schema if not exists auth;
create table if not exists auth.users (
  id uuid primary key
);

do $$
begin
  if to_regprocedure('auth.uid()') is null then
    execute $fn$
      create function auth.uid()
      returns uuid
      language sql
      stable
      as 'select nullif(current_setting(''request.jwt.claim.sub'', true), '''')::uuid'
    $fn$;
  end if;
end
$$;

create extension if not exists pgcrypto;

create type public.app_role as enum (
  'admin', 'cms_editor', 'admissions', 'teacher', 'teaching_assistant', 'student'
);
create type public.publish_status as enum ('draft', 'scheduled', 'published', 'archived');
create type public.lead_status as enum ('new', 'contacted', 'qualified', 'converted', 'lost');
create type public.class_status as enum ('planned', 'open', 'active', 'completed', 'cancelled');
create type public.enrollment_status as enum ('pending', 'active', 'paused', 'completed', 'withdrawn');
create type public.session_status as enum ('scheduled', 'completed', 'cancelled');
create type public.attendance_status as enum ('present', 'late', 'left_early', 'absent', 'excused');
create type public.assignment_status as enum ('draft', 'published', 'closed', 'archived');
create type public.submission_status as enum ('draft', 'submitted', 'late', 'returned', 'graded');
create type public.question_type as enum ('single_choice', 'multiple_choice', 'short_text', 'long_text', 'writing');
create type public.attempt_status as enum ('in_progress', 'submitted', 'graded', 'expired');
create type public.evaluation_status as enum ('queued', 'processing', 'ai_completed', 'teacher_reviewed', 'published', 'failed');
create type public.job_status as enum ('pending', 'processing', 'completed', 'failed', 'cancelled');
create type public.skill_type as enum ('listening', 'reading', 'writing', 'speaking', 'vocabulary', 'general');
create type public.activity_type as enum (
  'online_test', 'manual_result', 'writing_submission', 'audio_submission',
  'file_submission', 'vocabulary_list', 'checklist', 'external_link'
);
create type public.completion_method as enum ('web_only', 'manual_only', 'web_or_manual');
create type public.activity_attempt_status as enum (
  'not_started', 'in_progress', 'submitted', 'late', 'waiting_review',
  'needs_revision', 'completed', 'cancelled'
);
create type public.result_source as enum ('web', 'student_manual', 'teacher_manual', 'imported');
create type public.review_status as enum ('not_required', 'pending', 'verified', 'revision_requested', 'rejected');

-- -----------------------------------------------------------------------------
-- 1. Identity and access
-- -----------------------------------------------------------------------------

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null,
  email text,
  phone text,
  avatar_path text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.user_roles (
  user_id uuid not null references public.profiles(id) on delete cascade,
  role public.app_role not null,
  assigned_by uuid references public.profiles(id) on delete set null,
  assigned_at timestamptz not null default now(),
  primary key (user_id, role)
);

create table public.student_profiles (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  student_code text not null unique,
  current_band numeric(2,1) check (current_band between 0 and 9),
  target_band numeric(2,1) check (target_band between 0 and 9),
  date_of_birth date,
  address text,
  emergency_contact jsonb not null default '{}'::jsonb,
  joined_at date not null default current_date,
  notes text
);

create table public.teacher_profiles (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  teacher_code text not null unique,
  bio text,
  specialties text[] not null default '{}',
  certificates jsonb not null default '[]'::jsonb,
  years_experience smallint check (years_experience >= 0),
  joined_at date not null default current_date,
  notes text
);

-- -----------------------------------------------------------------------------
-- 2. Marketing, CMS and admissions
-- -----------------------------------------------------------------------------

create table public.campaigns (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  source text,
  medium text,
  campaign_code text unique,
  starts_at timestamptz,
  ends_at timestamptz,
  budget numeric(12,2) check (budget is null or budget >= 0),
  is_active boolean not null default true,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now()
);

create table public.cms_pages (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  excerpt text,
  content jsonb not null default '{}'::jsonb,
  seo_metadata jsonb not null default '{}'::jsonb,
  status public.publish_status not null default 'draft',
  published_at timestamptz,
  created_by uuid references public.profiles(id) on delete set null,
  updated_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.cms_posts (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  excerpt text,
  content jsonb not null default '{}'::jsonb,
  cover_path text,
  tags text[] not null default '{}',
  status public.publish_status not null default 'draft',
  published_at timestamptz,
  author_id uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.cms_banners (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  subtitle text,
  media_path text,
  target_url text,
  position text not null default 'home_hero',
  display_order integer not null default 0,
  starts_at timestamptz,
  ends_at timestamptz,
  status public.publish_status not null default 'draft',
  campaign_id uuid references public.campaigns(id) on delete set null,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (ends_at is null or starts_at is null or ends_at > starts_at)
);

create table public.leads (
  id uuid primary key default gen_random_uuid(),
  full_name text not null,
  phone text,
  email text,
  current_band numeric(2,1) check (current_band between 0 and 9),
  target_band numeric(2,1) check (target_band between 0 and 9),
  interested_course_id uuid,
  preferred_contact_at timestamptz,
  source text,
  utm_data jsonb not null default '{}'::jsonb,
  consent_at timestamptz,
  status public.lead_status not null default 'new',
  campaign_id uuid references public.campaigns(id) on delete set null,
  assigned_to uuid references public.profiles(id) on delete set null,
  converted_student_id uuid references public.student_profiles(user_id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (phone is not null or email is not null)
);

create table public.lead_contact_logs (
  id uuid primary key default gen_random_uuid(),
  lead_id uuid not null references public.leads(id) on delete cascade,
  contacted_by uuid references public.profiles(id) on delete set null,
  channel text not null check (channel in ('phone', 'email', 'zalo', 'in_person', 'other')),
  outcome text,
  notes text,
  contacted_at timestamptz not null default now(),
  next_contact_at timestamptz
);

-- -----------------------------------------------------------------------------
-- 3. Academic structure
-- -----------------------------------------------------------------------------

create table public.courses (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  description text,
  level text,
  target_band numeric(2,1) check (target_band between 0 and 9),
  total_sessions smallint check (total_sessions > 0),
  tuition_amount numeric(12,2) check (tuition_amount is null or tuition_amount >= 0),
  is_public boolean not null default false,
  is_active boolean not null default true,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.leads
  add constraint leads_interested_course_fk
  foreign key (interested_course_id) references public.courses(id) on delete set null;

create table public.classes (
  id uuid primary key default gen_random_uuid(),
  course_id uuid not null references public.courses(id) on delete restrict,
  code text not null unique,
  name text not null,
  capacity smallint not null check (capacity > 0),
  starts_on date not null,
  ends_on date,
  status public.class_status not null default 'planned',
  default_zoom_url text,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (ends_on is null or ends_on >= starts_on)
);

create table public.class_teachers (
  class_id uuid not null references public.classes(id) on delete cascade,
  teacher_id uuid not null references public.teacher_profiles(user_id) on delete restrict,
  teaching_role public.app_role not null check (teaching_role in ('teacher', 'teaching_assistant')),
  assigned_at timestamptz not null default now(),
  primary key (class_id, teacher_id)
);

create table public.enrollments (
  id uuid primary key default gen_random_uuid(),
  class_id uuid not null references public.classes(id) on delete restrict,
  student_id uuid not null references public.student_profiles(user_id) on delete restrict,
  status public.enrollment_status not null default 'pending',
  enrolled_at timestamptz not null default now(),
  started_on date,
  ended_on date,
  notes text,
  unique (class_id, student_id),
  check (ended_on is null or started_on is null or ended_on >= started_on)
);

create table public.class_sessions (
  id uuid primary key default gen_random_uuid(),
  class_id uuid not null references public.classes(id) on delete cascade,
  session_no smallint not null check (session_no > 0),
  title text,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  zoom_meeting_id text,
  zoom_url text,
  status public.session_status not null default 'scheduled',
  notes text,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  unique (class_id, session_no),
  check (ends_at > starts_at)
);

-- -----------------------------------------------------------------------------
-- 3B. Course curriculum and learning-progress model
-- A reusable course curriculum is separated from the schedule of a concrete class.
-- -----------------------------------------------------------------------------

create table public.course_modules (
  id uuid primary key default gen_random_uuid(),
  course_id uuid not null references public.courses(id) on delete cascade,
  title text not null,
  description text,
  skill public.skill_type not null,
  display_order smallint not null default 0,
  is_required boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (course_id, display_order)
);

create table public.learning_activities (
  id uuid primary key default gen_random_uuid(),
  module_id uuid not null references public.course_modules(id) on delete cascade,
  title text not null,
  description text,
  skill public.skill_type not null,
  activity_type public.activity_type not null,
  completion_method public.completion_method not null,
  display_order smallint not null default 0,
  is_required boolean not null default true,
  default_max_score numeric(7,2) check (default_max_score is null or default_max_score > 0),
  expected_duration_minutes smallint check (expected_duration_minutes is null or expected_duration_minutes > 0),
  requires_teacher_review boolean not null default false,
  requires_error_analysis boolean not null default false,
  requires_comprehension_rating boolean not null default false,
  allowed_evidence_types text[] not null default '{}',
  configuration jsonb not null default '{}'::jsonb,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (module_id, display_order)
);

create table public.activity_checklist_items (
  id uuid primary key default gen_random_uuid(),
  activity_id uuid not null references public.learning_activities(id) on delete cascade,
  label text not null,
  description text,
  display_order smallint not null default 0,
  is_required boolean not null default true,
  unique (activity_id, display_order)
);

create table public.class_activities (
  id uuid primary key default gen_random_uuid(),
  class_id uuid not null references public.classes(id) on delete cascade,
  session_id uuid references public.class_sessions(id) on delete set null,
  activity_id uuid not null references public.learning_activities(id) on delete restrict,
  assignment_id uuid,
  test_assignment_id uuid,
  opens_at timestamptz,
  due_at timestamptz,
  is_required_override boolean,
  max_score_override numeric(7,2) check (max_score_override is null or max_score_override > 0),
  instructions_override text,
  is_published boolean not null default false,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (due_at is null or opens_at is null or due_at > opens_at),
  check ((assignment_id is not null)::integer + (test_assignment_id is not null)::integer <= 1)
);

create table public.student_activity_attempts (
  id uuid primary key default gen_random_uuid(),
  class_activity_id uuid not null references public.class_activities(id) on delete cascade,
  student_id uuid not null references public.student_profiles(user_id) on delete restrict,
  attempt_no smallint not null default 1 check (attempt_no > 0),
  source public.result_source not null,
  status public.activity_attempt_status not null default 'not_started',
  review_status public.review_status not null default 'not_required',
  test_attempt_id uuid,
  submission_id uuid,
  score numeric(7,2),
  max_score numeric(7,2) check (max_score is null or max_score > 0),
  correct_count integer check (correct_count is null or correct_count >= 0),
  incorrect_count integer check (incorrect_count is null or incorrect_count >= 0),
  unanswered_count integer check (unanswered_count is null or unanswered_count >= 0),
  duration_seconds integer check (duration_seconds is null or duration_seconds >= 0),
  comprehension_percent smallint check (comprehension_percent between 0 and 100),
  error_analysis text,
  improvement_plan text,
  reflection text,
  started_at timestamptz,
  submitted_at timestamptz,
  completed_at timestamptz,
  is_late boolean not null default false,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (class_activity_id, student_id, attempt_no),
  check (score is null or max_score is null or score between 0 and max_score),
  check ((test_attempt_id is not null)::integer + (submission_id is not null)::integer <= 1)
);

create table public.attempt_checklist_results (
  attempt_id uuid not null references public.student_activity_attempts(id) on delete cascade,
  checklist_item_id uuid not null references public.activity_checklist_items(id) on delete cascade,
  is_completed boolean not null default false,
  completed_at timestamptz,
  note text,
  primary key (attempt_id, checklist_item_id)
);

create table public.attempt_error_reasons (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.student_activity_attempts(id) on delete cascade,
  reason_code text not null check (reason_code in (
    'unknown_vocabulary', 'question_misunderstood', 'lost_focus', 'audio_too_fast',
    'instruction_misread', 'time_shortage', 'wrong_inference', 'spelling',
    'grammar', 'pronunciation', 'other'
  )),
  note text,
  unique (attempt_id, reason_code)
);

create table public.student_vocabulary (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.student_profiles(user_id) on delete cascade,
  attempt_id uuid references public.student_activity_attempts(id) on delete cascade,
  term text not null,
  meaning text,
  example_sentence text,
  mastery_level smallint not null default 0 check (mastery_level between 0 and 5),
  created_at timestamptz not null default now(),
  last_reviewed_at timestamptz
);

create table public.activity_evidence (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.student_activity_attempts(id) on delete cascade,
  evidence_type text not null check (evidence_type in ('image', 'audio', 'video', 'document', 'external_link')),
  storage_path text,
  external_url text,
  original_name text,
  mime_type text,
  size_bytes bigint check (size_bytes is null or size_bytes >= 0),
  duration_seconds integer check (duration_seconds is null or duration_seconds >= 0),
  uploaded_at timestamptz not null default now(),
  check ((storage_path is not null)::integer + (external_url is not null)::integer = 1)
);

create table public.teacher_activity_reviews (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.student_activity_attempts(id) on delete cascade,
  reviewer_id uuid not null references public.teacher_profiles(user_id) on delete restrict,
  status public.review_status not null,
  verified_score numeric(7,2),
  feedback text,
  private_note text,
  adjustment_reason text,
  reviewed_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- 4. Zoom attendance
-- -----------------------------------------------------------------------------

create table public.attendance_imports (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.class_sessions(id) on delete cascade,
  source text not null check (source in ('csv', 'zoom_api', 'webhook')),
  source_file_path text,
  external_meeting_id text,
  imported_by uuid references public.profiles(id) on delete set null,
  imported_at timestamptz not null default now(),
  metadata jsonb not null default '{}'::jsonb
);

create table public.zoom_participants (
  id uuid primary key default gen_random_uuid(),
  import_id uuid not null references public.attendance_imports(id) on delete cascade,
  external_participant_id text,
  display_name text not null,
  email text,
  joined_at timestamptz,
  left_at timestamptz,
  duration_seconds integer check (duration_seconds is null or duration_seconds >= 0),
  raw_payload jsonb not null default '{}'::jsonb
);

create table public.attendance_records (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.class_sessions(id) on delete cascade,
  student_id uuid not null references public.student_profiles(user_id) on delete restrict,
  participant_id uuid references public.zoom_participants(id) on delete set null,
  status public.attendance_status not null,
  joined_at timestamptz,
  left_at timestamptz,
  duration_seconds integer check (duration_seconds is null or duration_seconds >= 0),
  match_method text check (match_method in ('email', 'student_code', 'exact_name', 'fuzzy_name', 'manual')),
  match_confidence numeric(5,4) check (match_confidence between 0 and 1),
  is_confirmed boolean not null default false,
  confirmed_by uuid references public.profiles(id) on delete set null,
  confirmed_at timestamptz,
  adjustment_reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (session_id, student_id)
);

-- -----------------------------------------------------------------------------
-- 5. Assignments and submissions
-- -----------------------------------------------------------------------------

create table public.assignments (
  id uuid primary key default gen_random_uuid(),
  class_id uuid not null references public.classes(id) on delete cascade,
  created_by uuid not null references public.profiles(id) on delete restrict,
  title text not null,
  description text,
  assignment_type text not null check (assignment_type in ('general', 'reading', 'listening', 'writing', 'vocabulary')),
  opens_at timestamptz,
  due_at timestamptz,
  max_score numeric(6,2) check (max_score is null or max_score > 0),
  allow_resubmission boolean not null default false,
  status public.assignment_status not null default 'draft',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (due_at is null or opens_at is null or due_at > opens_at)
);

create table public.assignment_resources (
  id uuid primary key default gen_random_uuid(),
  assignment_id uuid not null references public.assignments(id) on delete cascade,
  storage_path text not null,
  original_name text not null,
  mime_type text,
  size_bytes bigint check (size_bytes is null or size_bytes >= 0),
  uploaded_at timestamptz not null default now()
);

create table public.submissions (
  id uuid primary key default gen_random_uuid(),
  assignment_id uuid not null references public.assignments(id) on delete cascade,
  student_id uuid not null references public.student_profiles(user_id) on delete restrict,
  attempt_no smallint not null default 1 check (attempt_no > 0),
  text_content text,
  status public.submission_status not null default 'draft',
  submitted_at timestamptz,
  is_late boolean not null default false,
  extension_due_at timestamptz,
  extension_reason text,
  extension_granted_by uuid references public.profiles(id) on delete set null,
  score numeric(6,2),
  teacher_feedback text,
  graded_by uuid references public.profiles(id) on delete set null,
  graded_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (assignment_id, student_id, attempt_no)
);

create table public.submission_files (
  id uuid primary key default gen_random_uuid(),
  submission_id uuid not null references public.submissions(id) on delete cascade,
  storage_path text not null,
  original_name text not null,
  mime_type text,
  size_bytes bigint check (size_bytes is null or size_bytes >= 0),
  uploaded_at timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- 6. Tests and attempts
-- -----------------------------------------------------------------------------

create table public.tests (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  description text,
  duration_minutes smallint check (duration_minutes is null or duration_minutes > 0),
  status public.publish_status not null default 'draft',
  created_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.test_sections (
  id uuid primary key default gen_random_uuid(),
  test_id uuid not null references public.tests(id) on delete cascade,
  skill public.skill_type not null check (skill in ('reading', 'listening', 'writing')),
  title text not null,
  instructions text,
  display_order smallint not null default 0,
  duration_minutes smallint check (duration_minutes is null or duration_minutes > 0)
);

create table public.questions (
  id uuid primary key default gen_random_uuid(),
  section_id uuid not null references public.test_sections(id) on delete cascade,
  question_type public.question_type not null,
  prompt text not null,
  media_path text,
  metadata jsonb not null default '{}'::jsonb,
  correct_answer jsonb,
  max_score numeric(6,2) not null default 1 check (max_score > 0),
  display_order smallint not null default 0
);

create table public.question_options (
  id uuid primary key default gen_random_uuid(),
  question_id uuid not null references public.questions(id) on delete cascade,
  option_key text not null,
  content text not null,
  display_order smallint not null default 0,
  unique (question_id, option_key)
);

create table public.test_assignments (
  id uuid primary key default gen_random_uuid(),
  test_id uuid not null references public.tests(id) on delete cascade,
  class_id uuid not null references public.classes(id) on delete cascade,
  assigned_by uuid not null references public.profiles(id) on delete restrict,
  opens_at timestamptz,
  closes_at timestamptz,
  max_attempts smallint not null default 1 check (max_attempts > 0),
  created_at timestamptz not null default now(),
  unique (test_id, class_id),
  check (closes_at is null or opens_at is null or closes_at > opens_at)
);

create table public.test_attempts (
  id uuid primary key default gen_random_uuid(),
  test_assignment_id uuid not null references public.test_assignments(id) on delete cascade,
  student_id uuid not null references public.student_profiles(user_id) on delete restrict,
  attempt_no smallint not null check (attempt_no > 0),
  status public.attempt_status not null default 'in_progress',
  started_at timestamptz not null default now(),
  submitted_at timestamptz,
  auto_score numeric(7,2),
  final_score numeric(7,2),
  metadata jsonb not null default '{}'::jsonb,
  unique (test_assignment_id, student_id, attempt_no)
);

create table public.test_answers (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.test_attempts(id) on delete cascade,
  question_id uuid not null references public.questions(id) on delete restrict,
  answer jsonb not null default '{}'::jsonb,
  is_correct boolean,
  auto_score numeric(6,2),
  teacher_score numeric(6,2),
  teacher_feedback text,
  answered_at timestamptz not null default now(),
  unique (attempt_id, question_id)
);

-- Deferred foreign keys for the progress module. These targets are declared in
-- the Assignment and Test modules later in this bootstrap schema.
alter table public.class_activities
  add constraint class_activities_assignment_fk
  foreign key (assignment_id) references public.assignments(id) on delete set null;

alter table public.class_activities
  add constraint class_activities_test_assignment_fk
  foreign key (test_assignment_id) references public.test_assignments(id) on delete set null;

alter table public.student_activity_attempts
  add constraint student_activity_attempts_test_attempt_fk
  foreign key (test_attempt_id) references public.test_attempts(id) on delete set null;

alter table public.student_activity_attempts
  add constraint student_activity_attempts_submission_fk
  foreign key (submission_id) references public.submissions(id) on delete set null;

-- -----------------------------------------------------------------------------
-- 7. Writing AI with teacher review
-- -----------------------------------------------------------------------------

create table public.writing_evaluations (
  id uuid primary key default gen_random_uuid(),
  submission_id uuid references public.submissions(id) on delete cascade,
  test_answer_id uuid references public.test_answers(id) on delete cascade,
  task_type text not null check (task_type in ('academic_task_1', 'general_task_1', 'task_2')),
  essay_text text not null,
  status public.evaluation_status not null default 'queued',
  model_provider text,
  model_name text,
  rubric_version text not null,
  prompt_version text not null,
  overall_band_ai numeric(2,1) check (overall_band_ai between 0 and 9),
  overall_band_teacher numeric(2,1) check (overall_band_teacher between 0 and 9),
  strengths jsonb not null default '[]'::jsonb,
  improvements jsonb not null default '[]'::jsonb,
  detected_errors jsonb not null default '[]'::jsonb,
  raw_response jsonb,
  confidence numeric(5,4) check (confidence between 0 and 1),
  processing_ms integer check (processing_ms is null or processing_ms >= 0),
  error_message text,
  reviewed_by uuid references public.profiles(id) on delete set null,
  reviewed_at timestamptz,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((submission_id is not null)::integer + (test_answer_id is not null)::integer = 1)
);

create table public.writing_criterion_scores (
  id uuid primary key default gen_random_uuid(),
  evaluation_id uuid not null references public.writing_evaluations(id) on delete cascade,
  criterion text not null check (criterion in (
    'task_achievement', 'task_response', 'coherence_cohesion',
    'lexical_resource', 'grammatical_range_accuracy'
  )),
  ai_band numeric(2,1) not null check (ai_band between 0 and 9),
  teacher_band numeric(2,1) check (teacher_band between 0 and 9),
  ai_feedback text,
  teacher_feedback text,
  evidence jsonb not null default '[]'::jsonb,
  unique (evaluation_id, criterion)
);

-- -----------------------------------------------------------------------------
-- 8. Notifications, jobs, settings and audit
-- -----------------------------------------------------------------------------

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  type text not null,
  title text not null,
  body text not null,
  data jsonb not null default '{}'::jsonb,
  read_at timestamptz,
  created_at timestamptz not null default now()
);

create table public.background_jobs (
  id uuid primary key default gen_random_uuid(),
  job_type text not null,
  reference_type text,
  reference_id uuid,
  payload jsonb not null default '{}'::jsonb,
  status public.job_status not null default 'pending',
  attempts smallint not null default 0 check (attempts >= 0),
  max_attempts smallint not null default 3 check (max_attempts > 0),
  scheduled_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz,
  error_message text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.system_settings (
  key text primary key,
  value jsonb not null,
  description text,
  updated_by uuid references public.profiles(id) on delete set null,
  updated_at timestamptz not null default now()
);

create table public.audit_logs (
  id bigint generated always as identity primary key,
  actor_id uuid references public.profiles(id) on delete set null,
  action text not null,
  entity_type text not null,
  entity_id text,
  old_data jsonb,
  new_data jsonb,
  ip_address inet,
  user_agent text,
  created_at timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- 9. Indexes
-- -----------------------------------------------------------------------------

create index idx_user_roles_role on public.user_roles(role);
create index idx_leads_status_assigned on public.leads(status, assigned_to);
create index idx_leads_campaign on public.leads(campaign_id);
create index idx_leads_created_at on public.leads(created_at desc);
create index idx_cms_posts_status_published on public.cms_posts(status, published_at desc);
create index idx_classes_course_status on public.classes(course_id, status);
create index idx_enrollments_student_status on public.enrollments(student_id, status);
create index idx_enrollments_class_status on public.enrollments(class_id, status);
create index idx_sessions_class_time on public.class_sessions(class_id, starts_at);
create index idx_course_modules_course_skill on public.course_modules(course_id, skill, display_order);
create index idx_learning_activities_module_order on public.learning_activities(module_id, display_order);
create index idx_class_activities_class_due on public.class_activities(class_id, due_at);
create index idx_class_activities_session on public.class_activities(session_id);
create unique index uq_class_activities_with_session
  on public.class_activities(class_id, activity_id, session_id)
  where session_id is not null;
create unique index uq_class_activities_without_session
  on public.class_activities(class_id, activity_id)
  where session_id is null;
create index idx_activity_attempts_student_status on public.student_activity_attempts(student_id, status);
create index idx_activity_attempts_class_activity on public.student_activity_attempts(class_activity_id, submitted_at desc);
create index idx_activity_attempts_waiting_review on public.student_activity_attempts(review_status, submitted_at)
  where review_status = 'pending';
create index idx_activity_evidence_attempt on public.activity_evidence(attempt_id);
create index idx_student_vocabulary_student on public.student_vocabulary(student_id, last_reviewed_at);
create index idx_zoom_participants_import on public.zoom_participants(import_id);
create index idx_attendance_student on public.attendance_records(student_id, session_id);
create index idx_attendance_unconfirmed on public.attendance_records(session_id) where not is_confirmed;
create index idx_assignments_class_due on public.assignments(class_id, due_at);
create index idx_submissions_student_status on public.submissions(student_id, status);
create index idx_submissions_assignment on public.submissions(assignment_id, submitted_at);
create index idx_test_attempts_student on public.test_attempts(student_id, started_at desc);
create index idx_writing_evaluations_status on public.writing_evaluations(status, created_at);
create index idx_writing_raw_response_gin on public.writing_evaluations using gin(raw_response);
create index idx_notifications_unread on public.notifications(user_id, created_at desc) where read_at is null;
create index idx_jobs_ready on public.background_jobs(status, scheduled_at) where status = 'pending';
create index idx_audit_entity on public.audit_logs(entity_type, entity_id, created_at desc);
create index idx_audit_actor on public.audit_logs(actor_id, created_at desc);

-- -----------------------------------------------------------------------------
-- 10. Generic updated_at trigger
-- -----------------------------------------------------------------------------

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'profiles', 'cms_pages', 'cms_posts', 'cms_banners', 'leads', 'courses',
    'classes', 'course_modules', 'learning_activities', 'class_activities',
    'student_activity_attempts', 'attendance_records', 'assignments', 'submissions',
    'tests', 'writing_evaluations', 'background_jobs'
  ]
  loop
    execute format(
      'create trigger trg_%I_updated_at before update on public.%I '
      'for each row execute function public.set_updated_at()',
      table_name, table_name
    );
  end loop;
end;
$$;

-- -----------------------------------------------------------------------------
-- 11. Supabase RLS baseline
-- Detailed policies should be added in separate migrations and tested per role.
-- -----------------------------------------------------------------------------

create or replace function public.has_role(required_role public.app_role)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.user_roles
    where user_id = auth.uid() and role = required_role
  );
$$;

revoke all on function public.has_role(public.app_role) from public;
grant execute on function public.has_role(public.app_role) to authenticated;

alter table public.profiles enable row level security;
alter table public.user_roles enable row level security;
alter table public.student_profiles enable row level security;
alter table public.teacher_profiles enable row level security;
alter table public.enrollments enable row level security;
alter table public.class_sessions enable row level security;
alter table public.attendance_records enable row level security;
alter table public.course_modules enable row level security;
alter table public.learning_activities enable row level security;
alter table public.activity_checklist_items enable row level security;
alter table public.class_activities enable row level security;
alter table public.student_activity_attempts enable row level security;
alter table public.attempt_checklist_results enable row level security;
alter table public.attempt_error_reasons enable row level security;
alter table public.student_vocabulary enable row level security;
alter table public.activity_evidence enable row level security;
alter table public.teacher_activity_reviews enable row level security;
alter table public.assignments enable row level security;
alter table public.submissions enable row level security;
alter table public.test_attempts enable row level security;
alter table public.test_answers enable row level security;
alter table public.writing_evaluations enable row level security;
alter table public.writing_criterion_scores enable row level security;
alter table public.notifications enable row level security;

create policy profiles_read_self
  on public.profiles for select to authenticated
  using (id = auth.uid() or public.has_role('admin'));

create policy profiles_update_self
  on public.profiles for update to authenticated
  using (id = auth.uid() or public.has_role('admin'))
  with check (id = auth.uid() or public.has_role('admin'));

create policy notifications_read_self
  on public.notifications for select to authenticated
  using (user_id = auth.uid() or public.has_role('admin'));

create policy notifications_update_self
  on public.notifications for update to authenticated
  using (user_id = auth.uid() or public.has_role('admin'))
  with check (user_id = auth.uid() or public.has_role('admin'));

-- Public CMS read policies. Only published and currently active content is exposed.
alter table public.cms_pages enable row level security;
alter table public.cms_posts enable row level security;
alter table public.cms_banners enable row level security;
alter table public.courses enable row level security;

create policy cms_pages_public_read on public.cms_pages
  for select to anon, authenticated
  using (status = 'published' and (published_at is null or published_at <= now()));

create policy cms_posts_public_read on public.cms_posts
  for select to anon, authenticated
  using (status = 'published' and (published_at is null or published_at <= now()));

create policy cms_banners_public_read on public.cms_banners
  for select to anon, authenticated
  using (
    status = 'published'
    and (starts_at is null or starts_at <= now())
    and (ends_at is null or ends_at >= now())
  );

create policy courses_public_read on public.courses
  for select to anon, authenticated
  using (is_public and is_active);

-- Lead creation is normally routed through a server/Edge Function with rate limiting.
-- Do not add an unrestricted anonymous INSERT policy without CAPTCHA and validation.

comment on table public.background_jobs is
  'Persistent job metadata for Spring workers/schedulers. PostgreSQL remains the audit source if Redis is added later.';
comment on column public.writing_evaluations.raw_response is
  'Flexible provider response. Queryable fields required by the product remain normal columns.';
