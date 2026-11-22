-- Compatibility for legacy migrations written before V006 upper-cased app_role labels.
-- A callback has no Flyway checksum, so already-migrated environments remain valid.
do $$
begin
  if exists (
    select 1
    from pg_enum enum
    join pg_type type on type.oid = enum.enumtypid
    join pg_namespace namespace on namespace.oid = type.typnamespace
    where namespace.nspname = 'public' and type.typname = 'app_role' and enum.enumlabel = 'ADMIN'
  ) and to_regprocedure('public.has_role(app_role)') is not null then
    execute $function$
      create or replace function public.has_role(required_role text)
      returns boolean
      language sql
      stable
      security definer
      set search_path = public
      as $body$
        select public.has_role(upper(required_role)::public.app_role)
      $body$
    $function$;
    revoke all on function public.has_role(text) from public;
    grant execute on function public.has_role(text) to authenticated;
  end if;
end;
$$;
