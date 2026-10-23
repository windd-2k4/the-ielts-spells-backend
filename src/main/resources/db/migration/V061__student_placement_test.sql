create table if not exists public.student_placement_tests (
    id uuid primary key default gen_random_uuid(),
    student_id uuid not null references public.student_profiles(user_id) on delete cascade,
    status varchar(32) not null default 'IN_PROGRESS',
    
    -- Survey fields
    target_band numeric(2,1),
    exam_date date,
    study_hours_per_week integer,
    
    -- Fixed 5 attempts
    reading_attempt_id uuid references public.test_attempts(id) on delete set null,
    listening_attempt_id uuid references public.test_attempts(id) on delete set null,
    writing_t1_attempt_id uuid references public.test_attempts(id) on delete set null,
    writing_t2_attempt_id uuid references public.test_attempts(id) on delete set null,
    speaking_attempt_id uuid references public.test_attempts(id) on delete set null,
    
    -- Estimated scores
    estimated_overall_band numeric(2,1),
    estimated_reading_band numeric(2,1),
    estimated_listening_band numeric(2,1),
    estimated_writing_band numeric(2,1),
    estimated_speaking_band numeric(2,1),
    
    -- Detailed analysis JSON (Images 6 - 10)
    detailed_report jsonb default '{}'::jsonb,
    
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_student_placement_tests_student on public.student_placement_tests(student_id);

alter table public.student_placement_tests enable row level security;

create policy "Students can view their own placement tests"
    on public.student_placement_tests for select
    using (auth.uid() = student_id);

create policy "Students can insert their own placement tests"
    on public.student_placement_tests for insert
    with check (auth.uid() = student_id);

create policy "Students can update their own placement tests"
    on public.student_placement_tests for update
    using (auth.uid() = student_id)
    with check (auth.uid() = student_id);
