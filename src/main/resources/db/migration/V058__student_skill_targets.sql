alter table public.student_profiles
  add column target_listening_band numeric(2,1) check (target_listening_band between 0 and 9),
  add column target_reading_band numeric(2,1) check (target_reading_band between 0 and 9),
  add column target_writing_band numeric(2,1) check (target_writing_band between 0 and 9),
  add column target_speaking_band numeric(2,1) check (target_speaking_band between 0 and 9);

update public.student_profiles
set target_listening_band = target_band,
    target_reading_band = target_band,
    target_writing_band = target_band,
    target_speaking_band = target_band
where target_band is not null;
