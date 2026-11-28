alter table public.cms_pages
  add column if not exists published_title text,
  add column if not exists published_excerpt text,
  add column if not exists published_content jsonb,
  add column if not exists published_seo_metadata jsonb;

update public.cms_pages
set published_title = coalesce(published_title, title),
    published_excerpt = coalesce(published_excerpt, excerpt),
    published_content = coalesce(published_content, content),
    published_seo_metadata = coalesce(published_seo_metadata, seo_metadata)
where status in ('PUBLISHED'::public.publish_status, 'SCHEDULED'::public.publish_status);

comment on column public.cms_pages.content is
  'Editable draft content. Public readers must use the published snapshot.';
comment on column public.cms_pages.published_content is
  'Immutable public snapshot copied from the draft by the publish workflow.';

-- The public website reads CMS pages through /api/v1/public/home, which maps
-- only the published snapshot. Direct table reads would also reveal the draft
-- columns from the same row, so the base table is no longer exposed to clients.
drop policy if exists cms_pages_public_read on public.cms_pages;
