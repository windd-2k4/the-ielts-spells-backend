-- Bounded adaptive-learning projection for the first Reading vertical slice.
-- External aggregate ids are intentionally not foreign keys: this module owns
-- its projection and consumes immutable attempt events from Testing.

create table public.adaptive_knowledge_components (
  code text primary key,
  name text not null,
  description text,
  skill public.skill_type not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.adaptive_knowledge_relations (
  prerequisite_code text not null references public.adaptive_knowledge_components(code) on delete cascade,
  target_code text not null references public.adaptive_knowledge_components(code) on delete cascade,
  relation_type text not null default 'PREREQUISITE'
    check (relation_type in ('PREREQUISITE', 'SUPPORTS')),
  primary key (prerequisite_code, target_code, relation_type),
  check (prerequisite_code <> target_code)
);

create table public.adaptive_question_type_mappings (
  question_type text primary key,
  component_code text not null references public.adaptive_knowledge_components(code) on delete cascade,
  created_at timestamptz not null default now()
);

create table public.adaptive_question_knowledge_mappings (
  test_version_id uuid not null,
  question_key text not null,
  component_code text not null references public.adaptive_knowledge_components(code) on delete cascade,
  primary_mapping boolean not null default true,
  created_at timestamptz not null default now(),
  primary key (test_version_id, question_key, component_code)
);

create unique index uq_adaptive_primary_question_mapping
  on public.adaptive_question_knowledge_mappings(test_version_id, question_key)
  where primary_mapping;

create table public.adaptive_content_mappings (
  id uuid primary key default gen_random_uuid(),
  component_code text not null references public.adaptive_knowledge_components(code) on delete cascade,
  target_type text not null check (target_type in ('PRACTICE_FILTER', 'TEST_VERSION', 'EXERCISE_TEMPLATE', 'LEARNING_RESOURCE')),
  target_id uuid,
  title text not null,
  href text not null,
  duration_minutes smallint check (duration_minutes is null or duration_minutes > 0),
  difficulty text check (difficulty is null or difficulty in ('EASY', 'MEDIUM', 'HARD')),
  priority smallint not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (component_code, target_type, href)
);

create index idx_adaptive_content_candidate
  on public.adaptive_content_mappings(component_code, active, priority desc);

create table public.adaptive_learning_events (
  id uuid primary key,
  source_event_id uuid not null unique,
  event_type text not null,
  learner_id uuid not null,
  attempt_id uuid not null,
  occurred_at timestamptz not null,
  payload jsonb not null,
  processed_at timestamptz,
  created_at timestamptz not null default now()
);

create index idx_adaptive_learning_events_learner
  on public.adaptive_learning_events(learner_id, occurred_at desc);

create table public.adaptive_learner_knowledge_states (
  learner_id uuid not null,
  component_code text not null references public.adaptive_knowledge_components(code) on delete cascade,
  mastery_probability numeric(5,4) not null default 0.5000
    check (mastery_probability between 0 and 1),
  confidence numeric(5,4) not null default 0
    check (confidence between 0 and 1),
  evidence_count integer not null default 0 check (evidence_count >= 0),
  correct_count integer not null default 0 check (correct_count >= 0),
  model_version text not null default 'weighted-average-v1',
  last_evidence_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (learner_id, component_code),
  check (correct_count <= evidence_count)
);

create index idx_adaptive_learner_states_priority
  on public.adaptive_learner_knowledge_states(learner_id, mastery_probability, updated_at desc);

create table public.adaptive_recommendations (
  id uuid primary key,
  learner_id uuid not null,
  component_code text not null references public.adaptive_knowledge_components(code) on delete restrict,
  content_mapping_id uuid not null references public.adaptive_content_mappings(id) on delete restrict,
  source_attempt_id uuid not null,
  title text not null,
  href text not null,
  reason text not null,
  duration_minutes smallint,
  difficulty text,
  mastery_at_decision numeric(5,4) not null,
  confidence_at_decision numeric(5,4) not null,
  model_version text not null,
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'SUPERSEDED', 'ACCEPTED', 'COMPLETED', 'DISMISSED')),
  accepted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index uq_adaptive_active_recommendation
  on public.adaptive_recommendations(learner_id)
  where status = 'ACTIVE';

create index idx_adaptive_recommendation_history
  on public.adaptive_recommendations(learner_id, created_at desc);

insert into public.adaptive_knowledge_components(code, name, description, skill) values
  ('reading.main-idea', 'Xác định ý chính', 'Nhận diện thông điệp chính của đoạn văn.', 'READING'),
  ('reading.topic-sentence', 'Nhận diện câu chủ đề', 'Xác định câu thể hiện trọng tâm của đoạn.', 'READING'),
  ('reading.main-vs-detail', 'Phân biệt ý chính và chi tiết', 'Không chọn tiêu đề chỉ vì một chi tiết nổi bật.', 'READING'),
  ('reading.paraphrase', 'Nhận diện paraphrase', 'Nhận ra cách diễn đạt tương đương giữa đoạn và tiêu đề.', 'READING')
on conflict (code) do nothing;

insert into public.adaptive_knowledge_relations(prerequisite_code, target_code, relation_type) values
  ('reading.topic-sentence', 'reading.main-idea', 'SUPPORTS'),
  ('reading.main-vs-detail', 'reading.main-idea', 'SUPPORTS'),
  ('reading.paraphrase', 'reading.main-idea', 'SUPPORTS')
on conflict do nothing;

-- Until content authors tag individual questions, Matching Headings supplies
-- evidence only for its primary component. Explicit question mappings override it.
insert into public.adaptive_question_type_mappings(question_type, component_code) values
  ('MATCHING_HEADINGS', 'reading.main-idea')
on conflict (question_type) do nothing;

-- A safe catalog filter makes the first recommendation actionable without
-- coupling this module to Testing tables. Specific content can replace it later.
insert into public.adaptive_content_mappings(
  id, component_code, target_type, title, href, duration_minutes, difficulty, priority
) values (
  'a86d0000-0000-4000-8000-000000000001',
  'reading.main-idea',
  'PRACTICE_FILTER',
  'Luyện Matching Headings',
  '/student/practice?skill=READING&questionTypes=MATCHING_HEADINGS',
  15,
  'MEDIUM',
  100
)
on conflict (component_code, target_type, href) do nothing;

