-- Business data is served by the Spring Boot API. Supabase remains the
-- authentication and object-storage provider; browser keys must not read or
-- mutate operational tables through PostgREST.
revoke all privileges on all tables in schema public from anon, authenticated;
revoke all privileges on all sequences in schema public from anon, authenticated;

-- Keep future Flyway-created objects private by default as well.
alter default privileges in schema public
  revoke all privileges on tables from anon, authenticated;
alter default privileges in schema public
  revoke all privileges on sequences from anon, authenticated;

comment on schema public is
  'Application schema. Business data is accessed through the backend API, not browser PostgREST clients.';
