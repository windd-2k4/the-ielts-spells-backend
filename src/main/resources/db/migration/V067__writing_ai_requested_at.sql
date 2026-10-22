alter table public.writing_evaluations add column ai_requested_at timestamptz;
update public.writing_evaluations set ai_requested_at=created_at where status <> 'NOT_ELIGIBLE';
create index idx_writing_ai_requested_at on public.writing_evaluations(ai_requested_at)
  where ai_requested_at is not null;
