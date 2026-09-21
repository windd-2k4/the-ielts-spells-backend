-- Flyway Migration V030: Add Crawl Hub and Open Source Test Scraping Tables

create table if not exists public.crawl_sources (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  source_url text not null,
  crawler_type text not null default 'scrapy' check (crawler_type in ('scrapy', 'playwright', 'rss', 'github', 'api')),
  target_skill public.skill_type not null default 'READING',
  config jsonb not null default '{}'::jsonb,
  schedule_cron text,
  is_active boolean not null default true,
  last_crawled_at timestamptz,
  last_status text check (last_status in ('idle', 'running', 'success', 'failed')),
  total_crawled integer not null default 0 check (total_crawled >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.raw_crawled_tests (
  id uuid primary key default gen_random_uuid(),
  source_id uuid references public.crawl_sources(id) on delete set null,
  source_test_id text,
  title text not null,
  skill public.skill_type not null default 'READING',
  source_url text,
  raw_payload jsonb not null default '{}'::jsonb,
  status text not null default 'pending' check (status in ('pending', 'parsed', 'imported', 'rejected')),
  imported_test_id uuid references public.tests(id) on delete set null,
  parsed_structure jsonb,
  error_message text,
  crawled_at timestamptz not null default now(),
  imported_at timestamptz
);

create index if not exists idx_raw_crawled_tests_status on public.raw_crawled_tests(status);
create index if not exists idx_raw_crawled_tests_source on public.raw_crawled_tests(source_id);
