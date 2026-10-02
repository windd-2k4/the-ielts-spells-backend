-- Structured solution content and teacher-authored Reading evidence belong to
-- the immutable published version, not to a mutable builder JSON document.

alter table public.test_version_questions
  add column reasoning_steps jsonb not null default '[]'::jsonb,
  add column trap_analysis text,
  add column vocabulary_notes text,
  add column related_lesson_url text,
  add column solution_visibility text not null default 'STUDENT_AFTER_SUBMIT'
    check (solution_visibility in ('TEACHER_ONLY', 'STUDENT_AFTER_ASSIGN', 'STUDENT_AFTER_SUBMIT'));

alter table public.test_version_questions
  add constraint uq_test_version_questions_id_version unique (id, test_version_id);

create table public.test_version_question_evidence (
  id uuid primary key default gen_random_uuid(),
  test_version_id uuid not null,
  question_id uuid not null,
  evidence_order smallint not null check (evidence_order >= 0),
  start_offset integer,
  end_offset integer,
  quote text,
  prefix_text text,
  suffix_text text,
  paragraph_key text,
  label text,
  evidence_mode text not null default 'DIRECT_QUOTE'
    check (evidence_mode in ('DIRECT_QUOTE', 'WHOLE_PARAGRAPH', 'NO_DIRECT_EVIDENCE')),
  created_at timestamptz not null default now(),
  unique (question_id, evidence_order),
  foreign key (question_id, test_version_id)
    references public.test_version_questions(id, test_version_id) on delete cascade,
  check (
    (evidence_mode = 'NO_DIRECT_EVIDENCE' and start_offset is null and end_offset is null)
    or (
      evidence_mode in ('DIRECT_QUOTE', 'WHOLE_PARAGRAPH')
      and start_offset is not null and end_offset is not null
      and start_offset >= 0 and end_offset > start_offset
    )
  )
);

create index idx_test_version_question_evidence_delivery
  on public.test_version_question_evidence(test_version_id, question_id, evidence_order);

comment on table public.test_version_question_evidence is
  'Immutable author evidence spans for a Reading question in a published test version.';
