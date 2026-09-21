alter table public.test_attempt_annotations
  drop constraint if exists test_attempt_annotations_annotation_type_check,
  drop constraint if exists test_attempt_annotations_color_check;

alter table public.test_attempt_annotations
  add constraint test_attempt_annotations_annotation_type_check
    check (annotation_type in ('HIGHLIGHT', 'NOTE', 'UNDERLINE', 'STRIKETHROUGH')),
  add constraint test_attempt_annotations_color_check
    check (color in ('YELLOW', 'GREEN', 'PINK', 'CYAN', 'RED', 'INK'));

comment on column public.test_attempt_annotations.annotation_type is
  'Visual annotation style: highlight, note, underline, or strikethrough.';
