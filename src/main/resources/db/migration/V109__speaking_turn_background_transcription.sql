-- Let deterministic examiner turns advance before STT finishes while keeping transcription restart-safe.

alter table public.speaking_exam_turns
  add column if not exists transcription_status text not null default 'NOT_REQUIRED'
    check (transcription_status in ('NOT_REQUIRED', 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
  add column if not exists transcription_attempts integer not null default 0
    check (transcription_attempts >= 0),
  add column if not exists transcription_token uuid,
  add column if not exists transcription_started_at timestamptz,
  add column if not exists transcription_next_retry_at timestamptz,
  add column if not exists transcription_error text,
  add column if not exists transcription_storage_read_ms bigint,
  add column if not exists transcription_processing_ms bigint;

update public.speaking_exam_turns
set transcription_status = case
  when submitted_at is null or part_no is null then 'NOT_REQUIRED'
  when transcript is not null and btrim(transcript) <> '' then 'COMPLETED'
  else 'PENDING'
end;

create index if not exists idx_speaking_exam_turns_transcription_pending
  on public.speaking_exam_turns(transcription_next_retry_at, submitted_at)
  where transcription_status = 'PENDING';

comment on column public.speaking_exam_turns.transcription_status is
  'Persistent background STT state. Banked turns may advance while status is PENDING.';
