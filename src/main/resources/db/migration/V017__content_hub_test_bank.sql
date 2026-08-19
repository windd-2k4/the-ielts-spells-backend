-- Real persistence for the Content Hub test bank and builder drafts.
create sequence if not exists public.test_code_seq start 1;

alter table public.tests
  add column if not exists code text,
  add column if not exists purpose text not null default 'PRACTICE',
  add column if not exists primary_skill public.skill_type not null default 'GENERAL',
  add column if not exists test_type text not null default 'SINGLE_SKILL',
  add column if not exists difficulty text not null default 'Chưa phân loại',
  add column if not exists version text not null default 'v1.0',
  add column if not exists tags jsonb not null default '[]'::jsonb,
  add column if not exists builder_content jsonb not null default '{}'::jsonb;

update public.tests
set code = 'TST-' || lpad(nextval('public.test_code_seq')::text, 6, '0')
where code is null;

alter table public.tests
  alter column code set default ('TST-' || lpad(nextval('public.test_code_seq')::text, 6, '0')),
  alter column code set not null,
  add constraint tests_code_unique unique (code),
  add constraint tests_purpose_check check (purpose in ('PLACEMENT', 'PRACTICE', 'PROGRESS', 'MOCK_TEST')),
  add constraint tests_type_check check (test_type in ('FULL_TEST', 'SINGLE_SKILL'));

alter table public.test_sections drop constraint if exists test_sections_skill_check;

create index if not exists idx_tests_content_hub
  on public.tests(status, primary_skill, purpose, updated_at desc);
