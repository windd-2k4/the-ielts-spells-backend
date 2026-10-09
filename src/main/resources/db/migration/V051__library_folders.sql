create table public.library_folders (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(trim(name)) between 1 and 120),
  course_id uuid references public.courses(id) on delete set null,
  created_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index uq_library_folders_name_scope
  on public.library_folders (lower(name), coalesce(course_id, '00000000-0000-0000-0000-000000000000'::uuid));
create index idx_library_folders_course on public.library_folders(course_id);

alter table public.learning_resources
  add column folder_id uuid references public.library_folders(id) on delete set null;
alter table public.exercise_templates
  add column folder_id uuid references public.library_folders(id) on delete set null;

create index idx_learning_resources_folder on public.learning_resources(folder_id, updated_at desc);
create index idx_exercise_templates_folder on public.exercise_templates(folder_id, updated_at desc);

create trigger trg_library_folders_updated_at
  before update on public.library_folders
  for each row execute function public.set_updated_at();

alter table public.library_folders enable row level security;

comment on table public.library_folders is
  'Drive-style folders for teaching resources; a folder may be linked to one course or remain custom.';
