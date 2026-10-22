-- Preserve the published prompt alongside the immutable submitted essay.
alter table public.writing_evaluations
  add column task_context jsonb not null default '{}'::jsonb,
  add column processing_token uuid,
  add column processing_started_at timestamptz,
  add column ai_strengths jsonb not null default '[]'::jsonb,
  add column ai_improvements jsonb not null default '[]'::jsonb;

update public.writing_evaluations e
set task_context = task.value
from public.test_attempt_responses r
join public.test_attempts a on a.id = r.attempt_id
join public.test_versions v on v.id = a.test_version_id
cross join lateral jsonb_array_elements(coalesce(v.builder_content->'tasks',
  v.builder_content->'writingTasks', '[]'::jsonb)) with ordinality task(value, ordinality)
where e.test_attempt_response_id = r.id
  and coalesce(task.value->>'id', 'writing-task-' || coalesce(task.value->>'taskNo', task.ordinality::text)) = r.question_key;

update public.writing_evaluations
set ai_strengths = coalesce(raw_response->'strengths', '[]'::jsonb),
    ai_improvements = coalesce(raw_response->'improvements', '[]'::jsonb);

-- Legacy manual reviews stored an empty object; evidence is a list of essay quotations.
update public.writing_criterion_scores set evidence='[]'::jsonb where evidence='{}'::jsonb;

-- Old workers have no ownership token. Make interrupted work explicitly retryable.
update public.writing_evaluations set status='FAILED',
  error_message='Tác vụ chấm bị gián đoạn. Vui lòng thử chấm lại.', updated_at=now()
where status='PROCESSING';

create table public.writing_evaluation_reviews (
  id uuid primary key default gen_random_uuid(),
  evaluation_id uuid not null references public.writing_evaluations(id) on delete cascade,
  reviewer_id uuid references public.profiles(id) on delete set null,
  review jsonb not null,
  created_at timestamptz not null default now()
);
create index idx_writing_evaluation_reviews_history
  on public.writing_evaluation_reviews(evaluation_id, created_at);
alter table public.writing_evaluation_reviews enable row level security;
