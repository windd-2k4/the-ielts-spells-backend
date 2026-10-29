create table if not exists public.test_attempt_question_reviews (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.test_attempts(id) on delete cascade,
  question_key text not null,
  reason_code text not null check (reason_code in (
    'UNKNOWN_VOCABULARY', 'LOST_FOCUS', 'DISTRACTOR', 'SPELLING',
    'MISHEARD', 'QUESTION_MISUNDERSTOOD', 'OTHER'
  )),
  note text,
  needs_retry boolean not null default false,
  vocabulary_term text,
  vocabulary_meaning text,
  reviewed_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (attempt_id, question_key)
);

create index if not exists idx_test_attempt_question_reviews_attempt
  on public.test_attempt_question_reviews(attempt_id, needs_retry, updated_at desc);

comment on table public.test_attempt_question_reviews is
  'Student reflection metadata for incorrect or unanswered version-pinned test questions.';
