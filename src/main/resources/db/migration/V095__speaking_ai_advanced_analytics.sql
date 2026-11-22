-- Add speech metrics and answer upgrades for advanced AI speaking evaluations.
-- V095 is intentional: V094 is already used by the Reading practice seed.

alter table public.speaking_evaluations
  add column if not exists speech_metrics jsonb not null default '{}'::jsonb,
  add column if not exists answer_upgrades jsonb not null default '[]'::jsonb;

comment on column public.speaking_evaluations.speech_metrics is
  'Speaking metrics. WPM and filler counts are deterministic; pause count is AI-estimated.';

comment on column public.speaking_evaluations.answer_upgrades is
  'Sentence-level Band 7.5+ rewrites with collocations and explanations.';
