alter table public.student_profiles
  add column planned_exam_date date,
  add column exam_plan_status text check (exam_plan_status in ('PLANNED', 'NONE')),
  add column learning_setup_completed_at timestamptz;

update public.student_profiles
set learning_setup_completed_at = now();
