-- File metadata is kept in PostgreSQL while bytes live in a storage provider.
-- A resource can keep its Drive link and optionally attach several private files.
alter table public.learning_resources
  alter column external_url drop not null;

create table public.learning_resource_files (
  id uuid primary key default gen_random_uuid(),
  resource_id uuid not null references public.learning_resources(id) on delete cascade,
  file_role text not null default 'MAIN' check (file_role in (
    'MAIN', 'ANSWER_KEY', 'TRANSCRIPT', 'VOCABULARY', 'AUDIO', 'THUMBNAIL', 'SUPPORTING'
  )),
  storage_provider text not null check (storage_provider in ('LOCAL', 'SUPABASE')),
  bucket_name text,
  object_path text not null,
  original_filename text not null,
  mime_type text not null,
  size_bytes bigint not null check (size_bytes > 0),
  checksum_sha256 text,
  uploaded_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  archived_at timestamptz,
  unique (storage_provider, bucket_name, object_path)
);

create index idx_learning_resource_files_resource
  on public.learning_resource_files(resource_id, created_at);

alter table public.learning_resource_files enable row level security;

comment on table public.learning_resource_files is
  'Private file metadata for learning resources. Content is stored in LOCAL development storage or Supabase Storage.';
comment on column public.learning_resource_files.file_role is
  'Purpose of an attached file: main material, key, transcript, vocabulary, audio, thumbnail or supporting file.';
