alter table public.curated_vocabulary_items
  add column if not exists chunk text,
  add column if not exists collocations jsonb,
  add column if not exists exercise_json jsonb;
