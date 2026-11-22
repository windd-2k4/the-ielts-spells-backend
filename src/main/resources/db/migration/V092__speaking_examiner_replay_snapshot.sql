-- Preserve the exact next turn returned for an accepted client turn.
-- This keeps idempotent retries stable even after the session has advanced.

alter table public.speaking_exam_turns
  add column if not exists accepted_next_turn_id uuid
  references public.speaking_exam_turns(id) on delete restrict;

create index if not exists idx_speaking_exam_turns_accepted_next
  on public.speaking_exam_turns(accepted_next_turn_id)
  where accepted_next_turn_id is not null;
