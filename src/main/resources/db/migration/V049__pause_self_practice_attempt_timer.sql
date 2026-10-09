alter table public.test_attempts
  add column if not exists paused_remaining_seconds bigint;

alter table public.test_attempts
  drop constraint if exists test_attempts_paused_remaining_seconds_check,
  add constraint test_attempts_paused_remaining_seconds_check
    check (paused_remaining_seconds is null or paused_remaining_seconds >= 0);

comment on column public.test_attempts.paused_remaining_seconds is
  'Remaining timer budget while a self-practice attempt is outside the player; null means the timer is running.';

-- Preserve unfinished self-practice attempts created before pause/resume existed.
update public.test_attempts attempt
set paused_remaining_seconds = case
  when attempt.expires_at > now()
    then greatest(1, floor(extract(epoch from attempt.expires_at - now()))::bigint)
  else greatest(60, version.duration_minutes * 60::bigint)
end
from public.test_versions version
where version.id = attempt.test_version_id
  and attempt.attempt_origin = 'SELF_PRACTICE'
  and attempt.status = 'IN_PROGRESS'
  and attempt.paused_remaining_seconds is null;
