create table public.student_vocabulary_topics (
  id uuid primary key default gen_random_uuid(),
  student_id uuid not null references public.student_profiles(user_id) on delete cascade,
  name text not null check (char_length(trim(name)) between 1 and 80),
  created_at timestamptz not null default now()
);

create unique index uq_student_vocabulary_topic_name
  on public.student_vocabulary_topics(student_id, lower(name));

alter table public.student_vocabulary
  add column topic_id uuid,
  add column part_of_speech text,
  add column note text,
  add column source_label text,
  add column updated_at timestamptz not null default now();

insert into public.student_vocabulary_topics (student_id, name)
select distinct student_id, 'Từ vựng của tôi'
from public.student_vocabulary;

update public.student_vocabulary vocabulary
set topic_id = topic.id
from public.student_vocabulary_topics topic
where topic.student_id = vocabulary.student_id
  and topic.name = 'Từ vựng của tôi';

alter table public.student_vocabulary
  alter column topic_id set not null,
  add constraint fk_student_vocabulary_topic
    foreign key (topic_id) references public.student_vocabulary_topics(id) on delete cascade;

create unique index uq_student_vocabulary_topic_term
  on public.student_vocabulary(student_id, topic_id, lower(term));

create index idx_student_vocabulary_topic
  on public.student_vocabulary(student_id, topic_id, created_at desc);

alter table public.student_vocabulary_topics enable row level security;

