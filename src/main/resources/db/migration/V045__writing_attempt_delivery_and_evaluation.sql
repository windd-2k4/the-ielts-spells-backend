-- Connect immutable Writing attempt responses to the teacher-reviewed evaluation workflow.

alter table public.writing_evaluations
  add column if not exists test_attempt_response_id uuid
  references public.test_attempt_responses(id) on delete cascade;

-- The original anonymous CHECK name may differ across restored databases.
do $$
declare
  source_check record;
begin
  for source_check in
    select constraint_name
      from information_schema.check_constraints
     where constraint_schema = 'public'
       and constraint_name in (
         select con.conname
           from pg_constraint con
           join pg_class rel on rel.oid = con.conrelid
           join pg_namespace nsp on nsp.oid = rel.relnamespace
          where nsp.nspname = 'public'
            and rel.relname = 'writing_evaluations'
            and con.contype = 'c'
            and pg_get_constraintdef(con.oid) like '%submission_id%'
            and pg_get_constraintdef(con.oid) like '%test_answer_id%'
       )
  loop
    execute format('alter table public.writing_evaluations drop constraint %I', source_check.constraint_name);
  end loop;
end $$;

alter table public.writing_evaluations
  add constraint writing_evaluations_source_check check (
    (submission_id is not null)::integer
      + (test_answer_id is not null)::integer
      + (test_attempt_response_id is not null)::integer = 1
  );

create unique index if not exists uq_writing_evaluations_attempt_response
  on public.writing_evaluations(test_attempt_response_id)
  where test_attempt_response_id is not null;

alter table public.writing_criterion_scores
  alter column ai_band drop not null;

comment on column public.writing_evaluations.test_attempt_response_id is
  'Immutable Writing response submitted through the version-pinned student test player.';
