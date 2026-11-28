create table if not exists public.student_companion_profiles (
  student_id uuid primary key,
  mascot_type text not null default 'OWL' check (mascot_type in ('DRAGON', 'OWL')),
  accent text not null default 'US' check (accent in ('US', 'UK')),
  cefr_level text not null default 'A2' check (cefr_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
  learning_goal text not null default 'IELTS' check (learning_goal in ('IELTS', 'COMMUNICATION', 'EXAM')),
  daily_target_xp integer not null default 100 check (daily_target_xp in (50, 100, 150)),
  favorite_categories jsonb not null default '["Đời sống hàng ngày","Ngụ ngôn & Triết lý"]'::jsonb,
  preferred_game text not null default 'SPEED_VOCAB',
  onboarding_completed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.student_companion_profiles enable row level security;
