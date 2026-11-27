-- Phase 3 & 4: server-persisted Part 2 preparation deadline and bounded Part 3 adaptive follow-up tracking.

alter table public.speaking_exam_sessions
  add column if not exists adaptive_follow_ups_used integer not null default 0
    check (adaptive_follow_ups_used >= 0),
  add column if not exists part_2_prep_deadline_at timestamptz;

alter table public.speaking_exam_turns
  add column if not exists is_adaptive_follow_up boolean not null default false,
  add column if not exists parent_turn_id uuid
    references public.speaking_exam_turns(id) on delete set null;

create index if not exists idx_speaking_exam_turns_parent
  on public.speaking_exam_turns(parent_turn_id)
  where parent_turn_id is not null;

comment on column public.speaking_exam_sessions.adaptive_follow_ups_used is
  'Number of dynamic adaptive follow-up questions persisted in this session (capped at policy max).';
comment on column public.speaking_exam_sessions.part_2_prep_deadline_at is
  'Server-authoritative UTC deadline for Part 2 preparation so refresh never grants extra time.';
comment on column public.speaking_exam_turns.is_adaptive_follow_up is
  'True when this turn was dynamically generated from the student response rather than planned from the bank.';
