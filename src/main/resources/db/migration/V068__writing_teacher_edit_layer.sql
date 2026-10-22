alter table public.writing_evaluations
  add column teacher_edited_text text,
  add column teacher_annotations jsonb not null default '[]'::jsonb,
  add column teacher_revision integer not null default 0;

comment on column public.writing_evaluations.teacher_edited_text is
  'Teacher working copy. essay_text remains the immutable student submission.';
comment on column public.writing_evaluations.teacher_annotations is
  'Serialized inline teacher comments and correction metadata for the current revision.';

create index idx_writing_teacher_revision
  on public.writing_evaluations(teacher_status, teacher_revision, updated_at desc);
