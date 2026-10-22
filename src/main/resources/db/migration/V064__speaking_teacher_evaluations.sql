-- Some existing installations recorded V053 without retaining this table.
-- Recreate the base Speaking evaluation store before adding the teacher workflow.
create table if not exists public.speaking_evaluations (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.test_attempts(id) on delete cascade,
  test_attempt_response_id uuid references public.test_attempt_responses(id) on delete cascade,
  student_id uuid not null,
  question_key text not null,
  audio_url text,
  transcript text,
  status text not null default 'COMPLETED',
  overall_band numeric(3,1),
  fluency_band numeric(3,1),
  lexical_band numeric(3,1),
  grammar_band numeric(3,1),
  pronunciation_band numeric(3,1),
  fluency_feedback text,
  lexical_feedback text,
  grammar_feedback text,
  pronunciation_feedback text,
  strengths jsonb not null default '[]'::jsonb,
  improvements jsonb not null default '[]'::jsonb,
  model_provider text,
  model_name text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_speaking_evaluations_attempt_question unique (attempt_id, question_key)
);

create index if not exists idx_speaking_evaluations_attempt
  on public.speaking_evaluations(attempt_id, question_key);

create index if not exists idx_speaking_evaluations_student
  on public.speaking_evaluations(student_id, created_at desc);

alter table public.speaking_evaluations enable row level security;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public'
      and tablename = 'speaking_evaluations'
      and policyname = 'speaking_evaluations_read_policy'
  ) then
    create policy speaking_evaluations_read_policy
      on public.speaking_evaluations for select to authenticated
      using (student_id = auth.uid() or public.has_role('ADMIN') or public.has_role('TEACHER'));
  end if;
end
$$;

alter table public.speaking_evaluations
  add column if not exists teacher_status text not null default 'AVAILABLE',
  add column if not exists overall_band_teacher numeric(3,1),
  add column if not exists fluency_band_teacher numeric(3,1),
  add column if not exists lexical_band_teacher numeric(3,1),
  add column if not exists grammar_band_teacher numeric(3,1),
  add column if not exists pronunciation_band_teacher numeric(3,1),
  add column if not exists teacher_feedback text,
  add column if not exists teacher_strengths jsonb not null default '[]'::jsonb,
  add column if not exists teacher_improvements jsonb not null default '[]'::jsonb,
  add column if not exists reviewed_by uuid references public.profiles(id) on delete set null,
  add column if not exists reviewed_at timestamptz,
  add column if not exists published_at timestamptz;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.speaking_evaluations'::regclass
      and conname = 'speaking_evaluations_teacher_status_check'
  ) then
    alter table public.speaking_evaluations
      add constraint speaking_evaluations_teacher_status_check
      check (teacher_status in ('AVAILABLE', 'REVIEWED', 'PUBLISHED'));
  end if;

  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.speaking_evaluations'::regclass
      and conname = 'speaking_evaluations_teacher_bands_check'
  ) then
    alter table public.speaking_evaluations
      add constraint speaking_evaluations_teacher_bands_check check (
        (overall_band_teacher is null or overall_band_teacher between 0 and 9)
        and (fluency_band_teacher is null or fluency_band_teacher between 0 and 9)
        and (lexical_band_teacher is null or lexical_band_teacher between 0 and 9)
        and (grammar_band_teacher is null or grammar_band_teacher between 0 and 9)
        and (pronunciation_band_teacher is null or pronunciation_band_teacher between 0 and 9)
      );
  end if;
end
$$;

create index if not exists idx_speaking_evaluations_teacher_queue
  on public.speaking_evaluations(teacher_status, created_at desc);

comment on table public.speaking_evaluations is
  'Evaluations and AI assessment scores for student IELTS Speaking attempts.';

comment on column public.speaking_evaluations.teacher_status is
  'Teacher grading lifecycle independent from the automatic Speaking evaluation.';
