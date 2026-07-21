-- Authentication is hosted by Supabase while the application database can run
-- in a separate PostgreSQL instance. In that topology the local auth.users table
-- cannot contain the remote Supabase user, so profiles.id must remain an external
-- identity reference rather than a local foreign key.
alter table public.profiles
  drop constraint if exists profiles_id_fkey;

comment on column public.profiles.id is
  'Supabase Auth user UUID; may reference a user in a remote Supabase project';
