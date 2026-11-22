-- Phase 4: deterministic AI Speaking Examiner route. Audio remains in private object storage.

create table public.speaking_exam_sessions (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null unique references public.test_attempts(id) on delete cascade,
  student_id uuid not null references public.student_profiles(user_id) on delete cascade,
  state text not null check (state in (
    'CREATED', 'MIC_CHECK', 'IDENTITY_CHECK', 'PART_1', 'PART_2_PREPARATION',
    'PART_2_LONG_TURN', 'PART_2_FOLLOW_UP', 'PART_3', 'CLOSING',
    'EVALUATION_QUEUED', 'ABORTED')),
  state_version bigint not null default 0 check (state_version >= 0),
  prompt_version text not null,
  rubric_version text not null,
  speech_model_version text not null,
  feature_extractor_version text not null,
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.speaking_exam_turns (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.speaking_exam_sessions(id) on delete cascade,
  sequence_no integer not null check (sequence_no > 0),
  exam_state text not null check (exam_state in (
    'MIC_CHECK', 'IDENTITY_CHECK', 'PART_1', 'PART_2_LONG_TURN',
    'PART_2_FOLLOW_UP', 'PART_3', 'CLOSING')),
  part_no smallint check (part_no between 1 and 3),
  kind text not null check (kind in ('CHECKPOINT', 'QUESTION', 'CUE_CARD', 'FOLLOW_UP', 'CLOSING')),
  topic_key text,
  prompt_text text not null,
  preparation_time_seconds integer check (preparation_time_seconds is null or preparation_time_seconds >= 0),
  answer_time_seconds integer check (answer_time_seconds is null or answer_time_seconds > 0),
  client_turn_id uuid,
  student_turn_id uuid unique,
  answer_storage_key text references public.speaking_audio_assets(storage_key) on delete restrict,
  transcript text,
  duration_ms integer check (duration_ms is null or duration_ms > 0),
  submitted_at timestamptz,
  accepted_state text check (accepted_state is null or accepted_state in (
    'IDENTITY_CHECK', 'PART_1', 'PART_2_PREPARATION', 'PART_2_FOLLOW_UP',
    'PART_3', 'CLOSING', 'EVALUATION_QUEUED')),
  accepted_state_version bigint check (accepted_state_version is null or accepted_state_version > 0),
  created_at timestamptz not null default now(),
  constraint uq_speaking_exam_turn_sequence unique (session_id, sequence_no),
  constraint speaking_exam_turn_topic_check check (
    (part_no in (2, 3) and topic_key is not null) or part_no is null or part_no = 1
  ),
  constraint speaking_exam_turn_answer_check check (
    (submitted_at is null and client_turn_id is null and student_turn_id is null
      and answer_storage_key is null and accepted_state is null and accepted_state_version is null)
    or
    (submitted_at is not null and client_turn_id is not null and student_turn_id is not null
      and answer_storage_key is not null and duration_ms is not null and accepted_state is not null
      and accepted_state_version is not null)
  )
);

create unique index uq_speaking_exam_turn_client_id
  on public.speaking_exam_turns(session_id, client_turn_id)
  where client_turn_id is not null;

create unique index uq_speaking_exam_turn_audio
  on public.speaking_exam_turns(answer_storage_key)
  where answer_storage_key is not null;

create index idx_speaking_exam_sessions_student
  on public.speaking_exam_sessions(student_id, updated_at desc);

create index idx_speaking_exam_turns_current
  on public.speaking_exam_turns(session_id, sequence_no)
  where submitted_at is null;

alter table public.speaking_exam_sessions enable row level security;
alter table public.speaking_exam_turns enable row level security;

comment on table public.speaking_exam_sessions is
  'Versioned deterministic route for an AI Speaking Examiner attempt.';
comment on table public.speaking_exam_turns is
  'Full immutable examiner route with idempotent student answer references.';
