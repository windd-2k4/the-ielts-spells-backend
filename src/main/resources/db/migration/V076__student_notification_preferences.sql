create table if not exists public.student_notification_preferences (
  student_id uuid primary key references public.student_profiles(user_id) on delete cascade,
  upcoming_class_reminder boolean not null default true,
  assignment_deadline_reminder boolean not null default true,
  grading_completed_alert boolean not null default true,
  weekly_progress_digest boolean not null default true,
  browser_sound_alert boolean not null default false,
  updated_at timestamptz not null default now()
);

alter table public.student_notification_preferences enable row level security;
