-- V053: Speaking test delivery, attempt evaluations and AI assessment persistence

create table if not exists public.speaking_evaluations (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.test_attempts(id) on delete cascade,
  test_attempt_response_id uuid references public.test_attempt_responses(id) on delete cascade,
  student_id uuid not null,
  question_key text not null,
  audio_url text,
  transcript text,
  status text not null default 'COMPLETED',
  overall_band numeric(3,1),
  fluency_band numeric(3,1),
  lexical_band numeric(3,1),
  grammar_band numeric(3,1),
  pronunciation_band numeric(3,1),
  fluency_feedback text,
  lexical_feedback text,
  grammar_feedback text,
  pronunciation_feedback text,
  strengths jsonb not null default '[]'::jsonb,
  improvements jsonb not null default '[]'::jsonb,
  model_provider text,
  model_name text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_speaking_evaluations_attempt_question unique (attempt_id, question_key)
);

create index if not exists idx_speaking_evaluations_attempt
  on public.speaking_evaluations(attempt_id, question_key);

create index if not exists idx_speaking_evaluations_student
  on public.speaking_evaluations(student_id, created_at desc);

-- RLS
alter table public.speaking_evaluations enable row level security;

create policy speaking_evaluations_read_policy
  on public.speaking_evaluations for select to authenticated
  using (student_id = auth.uid() or public.has_role('admin') or public.has_role('teacher'));

comment on table public.speaking_evaluations is
  'Evaluations and AI assessment scores for student IELTS Speaking attempts.';
