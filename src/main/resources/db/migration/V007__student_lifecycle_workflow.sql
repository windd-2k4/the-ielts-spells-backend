create table public.enrollment_reservations (
  id uuid primary key default gen_random_uuid(),
  enrollment_id uuid not null references public.enrollments(id) on delete restrict,
  status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED', 'USED', 'EXPIRED', 'CANCELLED')),
  reason text not null,
  sessions_consumed smallint not null default 0 check (sessions_consumed >= 0),
  sessions_remaining smallint not null default 0 check (sessions_remaining >= 0),
  credit_amount numeric(14,2) not null default 0 check (credit_amount >= 0),
  expires_on date,
  target_class_id uuid references public.classes(id) on delete set null,
  requested_at timestamptz not null default now(),
  approved_at timestamptz,
  approved_by uuid references public.profiles(id) on delete set null,
  notes text,
  updated_at timestamptz not null default now()
);

create unique index uq_pending_reservation_per_enrollment
  on public.enrollment_reservations(enrollment_id)
  where status = 'PENDING';
create index idx_reservations_enrollment on public.enrollment_reservations(enrollment_id, requested_at desc);

create table public.class_transfers (
  id uuid primary key default gen_random_uuid(),
  source_enrollment_id uuid not null references public.enrollments(id) on delete restrict,
  target_class_id uuid not null references public.classes(id) on delete restrict,
  target_enrollment_id uuid references public.enrollments(id) on delete restrict,
  reservation_id uuid references public.enrollment_reservations(id) on delete set null,
  status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  reason text not null,
  fee_adjustment numeric(14,2) not null default 0,
  requested_at timestamptz not null default now(),
  approved_at timestamptz,
  approved_by uuid references public.profiles(id) on delete set null,
  notes text,
  updated_at timestamptz not null default now()
);

create unique index uq_pending_transfer_per_enrollment
  on public.class_transfers(source_enrollment_id)
  where status = 'PENDING';
create index idx_transfers_source on public.class_transfers(source_enrollment_id, requested_at desc);

alter table public.enrollment_reservations enable row level security;
alter table public.class_transfers enable row level security;

