-- Adds application roles from public.user_roles to Supabase access tokens.
-- After deploying this migration, enable public.custom_access_token_hook in
-- Supabase Dashboard > Authentication > Hooks > Custom Access Token.

do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'supabase_auth_admin') then
    create role supabase_auth_admin nologin;
  end if;
end
$$;

create or replace function public.custom_access_token_hook(event jsonb)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  claims jsonb;
  roles jsonb;
begin
  select coalesce(jsonb_agg(ur.role::text order by ur.role::text), '[]'::jsonb)
    into roles
    from public.user_roles ur
   where ur.user_id = (event ->> 'user_id')::uuid;

  claims := event -> 'claims';
  claims := jsonb_set(claims, '{user_roles}', roles, true);

  return jsonb_set(event, '{claims}', claims);
end;
$$;

grant usage on schema public to supabase_auth_admin;
grant select on table public.user_roles to supabase_auth_admin;
revoke execute on function public.custom_access_token_hook(jsonb) from public, anon, authenticated;
grant execute on function public.custom_access_token_hook(jsonb) to supabase_auth_admin;

comment on function public.custom_access_token_hook(jsonb)
  is 'Supabase Custom Access Token Hook: injects public.user_roles into user_roles JWT claim.';
