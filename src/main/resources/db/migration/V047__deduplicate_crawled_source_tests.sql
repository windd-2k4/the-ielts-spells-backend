-- Prevent the same canonical source test from being staged more than once per crawl source.
-- Existing simulated rows remain valid because their OPEN_REF identifiers are already unique.

create unique index if not exists uq_raw_crawled_tests_source_test
  on public.raw_crawled_tests(source_id, source_test_id)
  where source_test_id is not null;
