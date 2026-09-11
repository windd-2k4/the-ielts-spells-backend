-- A scheduled CMS item becomes public at its configured release time without
-- needing an application-side status rewrite. Drafts and archived items stay
-- invisible to public/anonymous requests.
drop policy if exists cms_pages_public_read on public.cms_pages;
create policy cms_pages_public_read on public.cms_pages
  for select to anon, authenticated
  using (
    (status = 'PUBLISHED' and (published_at is null or published_at <= now()))
    or (status = 'SCHEDULED' and published_at is not null and published_at <= now())
  );

drop policy if exists cms_posts_public_read on public.cms_posts;
create policy cms_posts_public_read on public.cms_posts
  for select to anon, authenticated
  using (
    (status = 'PUBLISHED' and (published_at is null or published_at <= now()))
    or (status = 'SCHEDULED' and published_at is not null and published_at <= now())
  );

drop policy if exists cms_banners_public_read on public.cms_banners;
create policy cms_banners_public_read on public.cms_banners
  for select to anon, authenticated
  using (
    (
      (status = 'PUBLISHED' and (starts_at is null or starts_at <= now()))
      or (status = 'SCHEDULED' and starts_at is not null and starts_at <= now())
    ) and (ends_at is null or ends_at >= now())
  );
