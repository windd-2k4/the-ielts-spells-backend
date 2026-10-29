alter table public.submission_files
  add column if not exists prompt_key text,
  add column if not exists prompt_title text,
  add column if not exists sort_order integer not null default 0;

with ranked_audio as (
  select id,
    row_number() over (partition by submission_id order by uploaded_at, id) audio_no
  from public.submission_files
  where lower(coalesce(mime_type, '')) like 'audio/%' and prompt_key is null
)
update public.submission_files file
set prompt_key = 'response-' || ranked.audio_no::text,
    prompt_title = coalesce(nullif(file.original_name, ''), 'Bài nói ' || ranked.audio_no::text),
    sort_order = (ranked.audio_no - 1)::integer
from ranked_audio ranked
where file.id = ranked.id;

create unique index if not exists uq_submission_audio_prompt
  on public.submission_files(submission_id, prompt_key)
  where prompt_key is not null and lower(coalesce(mime_type, '')) like 'audio/%';

create table if not exists public.teacher_review_criteria (
  review_id uuid not null references public.teacher_activity_reviews(id) on delete cascade,
  criterion text not null check (criterion in (
    'TASK_ACHIEVEMENT', 'TASK_RESPONSE', 'COHERENCE_COHESION',
    'LEXICAL_RESOURCE', 'GRAMMATICAL_RANGE_ACCURACY',
    'FLUENCY_COHERENCE', 'PRONUNCIATION'
  )),
  score numeric(3,1) check (score is null or score between 0 and 9),
  feedback text,
  sort_order integer not null default 0,
  primary key (review_id, criterion)
);

create table if not exists public.teacher_review_annotations (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references public.teacher_activity_reviews(id) on delete cascade,
  start_offset integer not null check (start_offset >= 0),
  end_offset integer not null check (end_offset > start_offset),
  quoted_text text not null,
  comment text not null,
  suggestion text,
  sort_order integer not null default 0
);

create index if not exists idx_teacher_review_annotations_review
  on public.teacher_review_annotations(review_id, sort_order, start_offset);

alter table public.teacher_review_criteria enable row level security;
alter table public.teacher_review_annotations enable row level security;
