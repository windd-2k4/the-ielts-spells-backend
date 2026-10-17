alter table public.attendance_records
  add column if not exists interaction_count integer not null default 0;

alter table public.attendance_records
  drop constraint if exists ck_attendance_interaction_count;

alter table public.attendance_records
  add constraint ck_attendance_interaction_count check (interaction_count >= 0);
