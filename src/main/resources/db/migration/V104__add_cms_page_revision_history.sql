create table if not exists public.cms_page_revisions (
  id uuid primary key default gen_random_uuid(),
  page_id uuid not null references public.cms_pages(id) on delete cascade,
  revision_no bigint not null,
  slug text not null,
  title text not null,
  excerpt text,
  content jsonb not null default '{}'::jsonb,
  seo_metadata jsonb not null default '{}'::jsonb,
  source varchar(24) not null,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  constraint cms_page_revisions_page_revision_unique unique (page_id, revision_no)
);

create index if not exists idx_cms_page_revisions_page_created
  on public.cms_page_revisions (page_id, revision_no desc);

insert into public.cms_page_revisions (
  page_id, revision_no, slug, title, excerpt, content, seo_metadata, source, created_by, created_at
)
select
  page.id, 1, page.slug, page.title, page.excerpt, page.content, page.seo_metadata,
  'MIGRATED', coalesce(page.updated_by, page.created_by), page.updated_at
from public.cms_pages page
where not exists (
  select 1 from public.cms_page_revisions revision where revision.page_id = page.id
);

comment on table public.cms_page_revisions is
  'Immutable saved snapshots used to preview and restore CMS page drafts.';

-- Revisions are available only through the authenticated authoring API.
alter table public.cms_page_revisions enable row level security;
