create table if not exists public.student_portal_preferences (
  student_id uuid primary key references public.student_profiles(user_id) on delete cascade,
  headline varchar(120) not null default '',
  biography varchar(1000) not null default '',
  language varchar(2) not null default 'vi' check (language in ('vi', 'en')),
  show_profile_to_class boolean not null default true,
  show_leaderboard_score boolean not null default true,
  allow_teacher_feedback_share boolean not null default true,
  default_practice_skill varchar(12) not null default 'READING'
    check (default_practice_skill in ('READING', 'LISTENING', 'WRITING', 'SPEAKING')),
  exam_font_size varchar(2) not null default 'md' check (exam_font_size in ('sm', 'md', 'lg')),
  auto_save_answers boolean not null default true,
  countdown_warning boolean not null default true,
  focus_mode boolean not null default false,
  updated_at timestamptz not null default now()
);

alter table public.student_portal_preferences enable row level security;
