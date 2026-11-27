alter table public.speaking_exam_sessions
  add column variant text;

update public.speaking_exam_sessions
set variant = 'FULL_MOCK'
where variant is null;

alter table public.speaking_exam_sessions
  alter column variant set default 'FULL_MOCK',
  alter column variant set not null,
  add constraint speaking_exam_sessions_variant_check
    check (variant in ('FULL_MOCK', 'PART_1', 'PART_2', 'PART_3'));

comment on column public.speaking_exam_sessions.variant is
  'Immutable scope selected when the examiner session is created.';
