alter table public.writing_evaluations
  add column teacher_edited_html text,
  add column teacher_published_html text;

comment on column public.writing_evaluations.teacher_edited_html is
  'Sanitized rich-text HTML for the current teacher correction draft; teacher_edited_text remains the annotation/plain-text source.';
comment on column public.writing_evaluations.teacher_published_html is
  'Immutable sanitized rich-text snapshot released to the student on teacher publish.';
