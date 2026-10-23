-- Attempt-level IELTS Speaking assessment, AI budget and durable queue.

create table if not exists public.speaking_ai_entitlements (
  student_id uuid primary key references public.student_profiles(user_id) on delete cascade,
  enabled boolean not null default false,
  unlimited boolean not null default false,
  monthly_limit integer,
  active_from timestamptz not null default now(),
  expires_at timestamptz,
  source text not null default 'MANUAL',
  note text,
  granted_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint speaking_ai_entitlements_limit_check check (
    (not enabled)
    or (unlimited and monthly_limit is null)
    or (not unlimited and monthly_limit > 0)
  ),
  constraint speaking_ai_entitlements_window_check check (
    expires_at is null or expires_at > active_from
  )
);

alter table public.speaking_ai_entitlements enable row level security;

alter table public.speaking_evaluations
  add column if not exists ai_requested_at timestamptz,
  add column if not exists ai_completed_at timestamptz,
  add column if not exists error_message text,
  add column if not exists processing_token uuid,
  add column if not exists processing_started_at timestamptz,
  add column if not exists processing_ms integer,
  add column if not exists rubric_version text not null default 'ielts-speaking-public-v1',
  add column if not exists prompt_version text not null default 'speaking-evaluator-v2',
  add column if not exists fluency_feedback_teacher text,
  add column if not exists lexical_feedback_teacher text,
  add column if not exists grammar_feedback_teacher text,
  add column if not exists pronunciation_feedback_teacher text;

create index if not exists idx_speaking_ai_entitlements_active
  on public.speaking_ai_entitlements(enabled, expires_at);

create index if not exists idx_speaking_evaluations_ai_queue
  on public.speaking_evaluations(created_at)
  where status = 'QUEUED';

create index if not exists idx_speaking_evaluations_ai_usage
  on public.speaking_evaluations(student_id, ai_requested_at)
  where ai_requested_at is not null;

-- Make previously submitted attempts visible in the new whole-test queue without
-- reusing legacy per-question bands (those were not valid whole-test scores).
insert into public.speaking_evaluations(
  attempt_id, student_id, question_key, audio_url, transcript, status,
  rubric_version, prompt_version, created_at, updated_at
)
select attempt.id, attempt.student_id, '__ATTEMPT__',
  (array_agg(response.answer->>'audioUrl' order by response.answered_at)
    filter (where nullif(response.answer->>'audioUrl', '') is not null))[1],
  string_agg(response.question_key || ': ' || coalesce(response.answer->>'transcript', ''),
    E'\n\n' order by response.answered_at),
  'NOT_ELIGIBLE', 'ielts-speaking-public-v1', 'speaking-evaluator-v2',
  coalesce(attempt.submitted_at, attempt.updated_at), now()
from public.test_attempts attempt
join public.test_versions version on version.id=attempt.test_version_id
join public.test_attempt_responses response on response.attempt_id=attempt.id
  and response.question_type='SPEAKING_QUESTION'
where version.primary_skill='SPEAKING' and attempt.status <> 'IN_PROGRESS'
group by attempt.id, attempt.student_id, attempt.submitted_at, attempt.updated_at
on conflict (attempt_id, question_key) do nothing;

comment on table public.speaking_ai_entitlements is
  'Per-student access and monthly quota for automatic IELTS Speaking assessment.';
comment on column public.speaking_evaluations.question_key is
  'Uses __ATTEMPT__ for the canonical whole-test Speaking assessment.';
