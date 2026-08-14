create table public.schedule_templates (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  skill_pair text not null check (skill_pair in ('LISTENING_READING', 'SPEAKING_WRITING')),
  description text,
  definition_json text not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index uq_schedule_templates_name_skill
  on public.schedule_templates(lower(name), skill_pair) where active;

create trigger trg_schedule_templates_updated_at
  before update on public.schedule_templates
  for each row execute function public.set_updated_at();

alter table public.schedule_templates enable row level security;

comment on table public.schedule_templates is
  'Reusable course roadmaps. The JSON definition contains ordered session/test entries and their content suggestions.';
