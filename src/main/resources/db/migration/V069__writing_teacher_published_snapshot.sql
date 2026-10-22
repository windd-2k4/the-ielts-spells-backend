alter table public.writing_evaluations
  add column teacher_published_text text,
  add column teacher_published_annotations jsonb not null default '[]'::jsonb;

update public.writing_evaluations
set teacher_published_text = teacher_edited_text,
    teacher_published_annotations = teacher_annotations
where teacher_status = 'PUBLISHED';

comment on column public.writing_evaluations.teacher_published_text is
  'Immutable student-facing snapshot until the next teacher publication.';
