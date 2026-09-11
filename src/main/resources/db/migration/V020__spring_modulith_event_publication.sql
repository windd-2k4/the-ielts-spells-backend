-- Spring Modulith JPA event publication registry.
-- Hibernate DDL generation is disabled, so this infrastructure table is
-- managed explicitly by Flyway like the rest of the application schema.
create table public.event_publication (
  id uuid primary key,
  listener_id text not null,
  event_type text not null,
  serialized_event text not null,
  publication_date timestamptz not null,
  completion_date timestamptz
);

create index event_publication_serialized_event_hash_idx
  on public.event_publication using hash (serialized_event);

create index event_publication_by_completion_date_idx
  on public.event_publication (completion_date);
