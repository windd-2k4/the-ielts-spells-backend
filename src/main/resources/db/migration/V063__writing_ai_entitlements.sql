create table public.writing_ai_entitlements (
  student_id uuid primary key references public.student_profiles(user_id) on delete cascade,
  enabled boolean not null default false,
  unlimited boolean not null default false,
  monthly_limit integer,
  active_from timestamptz not null default now(),
  expires_at timestamptz,
  source text not null default 'MANUAL',
  note text,
  granted_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint writing_ai_entitlements_limit_check check (
    (not enabled)
    or (unlimited and monthly_limit is null)
    or (not unlimited and monthly_limit > 0)
  ),
  constraint writing_ai_entitlements_window_check check (
    expires_at is null or expires_at > active_from
  )
);

alter table public.writing_ai_entitlements enable row level security;

alter table public.writing_evaluations
  add column teacher_status text not null default 'AVAILABLE',
  add column ai_completed_at timestamptz;

alter table public.writing_evaluations
  add constraint writing_evaluations_teacher_status_check
  check (teacher_status in ('AVAILABLE', 'REVIEWED', 'PUBLISHED'));

update public.writing_evaluations
set teacher_status = case
      when published_at is not null then 'PUBLISHED'
      when reviewed_at is not null then 'REVIEWED'
      else 'AVAILABLE'
    end,
    ai_completed_at = case when overall_band_ai is not null then updated_at else null end,
    status = case
      when overall_band_ai is not null then 'AI_COMPLETED'::public.evaluation_status
      when status = 'FAILED' then 'FAILED'::public.evaluation_status
      when status in ('QUEUED', 'PROCESSING') then 'NOT_ELIGIBLE'::public.evaluation_status
      else 'NOT_ELIGIBLE'::public.evaluation_status
    end;

create index idx_writing_ai_entitlements_active
  on public.writing_ai_entitlements(enabled, expires_at);

create index idx_writing_evaluations_ai_usage
  on public.writing_evaluations(created_at, status)
  where status <> 'NOT_ELIGIBLE';

comment on table public.writing_ai_entitlements is
  'Per-student access and monthly quota for automatic Writing evaluation.';
comment on column public.writing_ai_entitlements.unlimited is
  'When true, the student is not subject to a per-student monthly limit; the global provider budget still applies.';
comment on column public.writing_evaluations.teacher_status is
  'Teacher grading lifecycle kept independent from the AI evaluation status.';
