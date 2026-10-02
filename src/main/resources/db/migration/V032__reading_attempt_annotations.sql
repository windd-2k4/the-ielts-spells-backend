create table if not exists public.test_attempt_annotations (
  id uuid primary key,
  attempt_id uuid not null references public.test_attempts(id) on delete cascade,
  section_key text not null,
  annotation_type text not null check (annotation_type in ('HIGHLIGHT', 'NOTE')),
  color text not null default 'YELLOW' check (color in ('YELLOW', 'GREEN', 'PINK')),
  start_offset integer not null check (start_offset >= 0),
  end_offset integer not null check (end_offset > start_offset),
  selected_text text not null check (char_length(selected_text) between 1 and 5000),
  prefix_text text not null default '',
  suffix_text text not null default '',
  note_text text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (annotation_type <> 'NOTE' or nullif(btrim(note_text), '') is not null)
);

create index if not exists idx_test_attempt_annotations_attempt_section
  on public.test_attempt_annotations(attempt_id, section_key, start_offset);

comment on table public.test_attempt_annotations is
  'Student-owned notes and text highlights anchored to immutable Reading passage content for one attempt.';
