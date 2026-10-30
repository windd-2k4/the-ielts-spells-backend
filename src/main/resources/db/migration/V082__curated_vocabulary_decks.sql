create table public.curated_vocabulary_decks (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null,
  category text not null check (category in ('BASIC', 'ADVANCED', 'INTENSIVE')),
  description text,
  word_count integer not null default 0,
  order_index integer not null default 0,
  created_at timestamptz not null default now()
);

create table public.curated_vocabulary_items (
  id uuid primary key default gen_random_uuid(),
  deck_id uuid not null references public.curated_vocabulary_decks(id) on delete cascade,
  term text not null,
  part_of_speech text,
  phonetic text,
  meaning text not null,
  example_sentence text,
  example_sentence_vi text,
  tip text,
  drills_json jsonb,
  order_index integer not null default 0,
  created_at timestamptz not null default now()
);

create index idx_curated_vocab_items_deck_order
  on public.curated_vocabulary_items(deck_id, order_index);

create index idx_curated_vocab_items_deck_term
  on public.curated_vocabulary_items(deck_id, lower(term));

create table public.student_curated_vocabulary_progress (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.student_profiles(user_id) on delete cascade,
  deck_id uuid not null references public.curated_vocabulary_decks(id) on delete cascade,
  item_id uuid not null references public.curated_vocabulary_items(id) on delete cascade,
  mastery_status text not null default 'NOT_LEARNED' check (mastery_status in ('NOT_LEARNED', 'FAMILIAR', 'MASTERED')),
  review_count integer not null default 0,
  last_reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index uq_student_curated_vocab_item
  on public.student_curated_vocabulary_progress(student_id, item_id);

create index idx_student_curated_vocab_progress
  on public.student_curated_vocabulary_progress(student_id, deck_id, mastery_status);

alter table public.curated_vocabulary_decks enable row level security;
alter table public.curated_vocabulary_items enable row level security;
alter table public.student_curated_vocabulary_progress enable row level security;
