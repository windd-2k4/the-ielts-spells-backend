-- PLANNED and OPEN represented the same pre-opening phase. Keep OPEN as the
-- single persisted state, then activate courses whose start date has arrived.
update public.courses
set status = 'OPEN',
    updated_at = now()
where status = 'PLANNED';

update public.courses
set status = 'ACTIVE',
    updated_at = now()
where status = 'OPEN'
  and starts_on <= current_date;
