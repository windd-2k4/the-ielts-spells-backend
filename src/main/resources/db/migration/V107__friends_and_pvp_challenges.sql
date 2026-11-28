create table if not exists public.student_friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null,
  addressee_id uuid not null,
  status text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'DECLINED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint chk_friend_not_self check (requester_id <> addressee_id)
);

create unique index if not exists uq_student_friendship_pair
  on public.student_friendships(least(requester_id, addressee_id), greatest(requester_id, addressee_id));

create index if not exists idx_friendships_addressee_status
  on public.student_friendships(addressee_id, status);

alter table public.student_friendships enable row level security;

create table if not exists public.pvp_challenges (
  id uuid primary key default gen_random_uuid(),
  challenger_id uuid not null,
  opponent_id uuid not null,
  game_mode text not null default 'SPEED_VOCAB',
  difficulty text not null default 'MEDIUM',
  wager_gems integer not null default 0 check (wager_gems >= 0 and wager_gems <= 100),
  status text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'COMPLETED', 'EXPIRED')),
  room_code text not null,
  challenger_score integer,
  opponent_score integer,
  winner_id uuid,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '15 minutes')
);

create index if not exists idx_pvp_challenges_opponent_status
  on public.pvp_challenges(opponent_id, status, created_at desc);

create index if not exists idx_pvp_challenges_challenger
  on public.pvp_challenges(challenger_id, created_at desc);

alter table public.pvp_challenges enable row level security;
