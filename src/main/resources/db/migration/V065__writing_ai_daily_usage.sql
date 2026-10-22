create table public.writing_ai_daily_usage (
  usage_date date primary key,
  started_count integer not null default 0,
  updated_at timestamptz not null default now(),
  constraint writing_ai_daily_usage_count_check check (started_count >= 0)
);

alter table public.writing_ai_daily_usage enable row level security;

insert into public.writing_ai_daily_usage(usage_date, started_count)
select current_date, count(*)::integer
from public.writing_evaluations
where processing_started_at >= date_trunc('day', now())
having count(*) > 0;

comment on table public.writing_ai_daily_usage is
  'Global Writing AI provider-call budget. Every claimed run, including retries, increments the current day.';
