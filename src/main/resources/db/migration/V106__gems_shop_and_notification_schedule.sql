create table if not exists public.student_wallets (
  student_id uuid primary key,
  gems integer not null default 30 check (gems >= 0),
  freeze_shields integer not null default 0 check (freeze_shields between 0 and 3),
  double_gem_until timestamptz,
  arena_hints integer not null default 0 check (arena_hints >= 0),
  equipped_skin text,
  equipped_title text,
  owned_items jsonb not null default '[]'::jsonb,
  pvp_rank_points integer not null default 120 check (pvp_rank_points >= 0),
  pvp_wins integer not null default 0 check (pvp_wins >= 0),
  pvp_losses integer not null default 0 check (pvp_losses >= 0),
  daily_routine_claims jsonb not null default '{}'::jsonb,
  streak_days integer not null default 0 check (streak_days >= 0),
  longest_streak integer not null default 0 check (longest_streak >= 0),
  last_active_date date,
  updated_at timestamptz not null default now()
);

alter table public.student_wallets enable row level security;

create table if not exists public.gem_transactions (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null,
  amount integer not null,
  source text not null,
  note text not null default '',
  created_at timestamptz not null default now()
);

create index if not exists idx_gem_tx_student_created
  on public.gem_transactions(student_id, created_at desc);

alter table public.gem_transactions enable row level security;

create table if not exists public.student_study_schedules (
  student_id uuid primary key,
  daily_reminder_enabled boolean not null default true,
  preferred_study_time text not null default '20:00',
  streak_saver_enabled boolean not null default true,
  pvp_challenge_alert boolean not null default true,
  srs_due_alert boolean not null default true,
  timezone text not null default 'Asia/Ho_Chi_Minh',
  updated_at timestamptz not null default now()
);

alter table public.student_study_schedules enable row level security;

alter table public.student_companion_profiles
  add column if not exists preferred_study_time text not null default '20:00';
