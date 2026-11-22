-- Private Speaking recordings. PostgreSQL stores only the generated storage key and verified metadata.

create table public.speaking_audio_assets (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.test_attempts(id) on delete cascade,
  student_id uuid not null references public.student_profiles(user_id) on delete cascade,
  question_key text not null,
  storage_key text not null unique,
  mime_type text not null,
  size_bytes bigint not null,
  duration_ms integer not null,
  created_at timestamptz not null default now(),
  constraint speaking_audio_assets_question_key_check check (char_length(question_key) between 1 and 200),
  constraint speaking_audio_assets_storage_key_check check (storage_key ~ '^speaking-attempts/[0-9a-f-]+/[0-9a-f-]+[.]webm$'),
  constraint speaking_audio_assets_mime_type_check check (mime_type = 'audio/webm'),
  constraint speaking_audio_assets_size_check check (size_bytes > 0),
  constraint speaking_audio_assets_duration_check check (duration_ms > 0)
);

create index idx_speaking_audio_assets_attempt_question
  on public.speaking_audio_assets(attempt_id, question_key, created_at desc);

alter table public.speaking_audio_assets enable row level security;

comment on table public.speaking_audio_assets is
  'Server-verified metadata for private WebM/Opus Speaking recordings; binary data remains in object storage.';
