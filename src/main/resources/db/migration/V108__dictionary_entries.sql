create table if not exists public.dictionary_entries (
  id bigserial primary key,
  direction text not null check (direction in ('EN_VI', 'VI_EN')),
  headword text not null,
  headword_lower text not null,
  normalized_headword text not null,
  ipa text,
  cefr_level text,
  part_of_speech text,
  summary text,
  payload jsonb not null default '{}'::jsonb,
  source text not null default 'AVDICT',
  created_at timestamptz not null default now()
);

create unique index if not exists uq_dictionary_direction_headword
  on public.dictionary_entries(direction, headword_lower);

create index if not exists idx_dictionary_dir_prefix
  on public.dictionary_entries(direction, headword_lower text_pattern_ops);

create index if not exists idx_dictionary_dir_norm_prefix
  on public.dictionary_entries(direction, normalized_headword text_pattern_ops);

create index if not exists idx_dictionary_headword_lower
  on public.dictionary_entries(headword_lower);

create table if not exists public.student_lookup_history (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null,
  query_text text not null check (char_length(trim(query_text)) between 1 and 500),
  direction text not null check (direction in ('EN_VI', 'VI_EN')),
  entry_type text not null default 'WORD' check (entry_type in ('WORD', 'TRANSLATE')),
  summary text,
  created_at timestamptz not null default now()
);

create index if not exists idx_student_lookup_history_student
  on public.student_lookup_history(student_id, created_at desc);

alter table public.dictionary_entries enable row level security;
alter table public.student_lookup_history enable row level security;
