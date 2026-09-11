-- Admin accounts are internal personnel too. Older installations created the
-- auth/profile/role rows directly, before staff_profiles was introduced.
-- Materialize those existing admins once so the staff directory is complete.
insert into public.staff_profiles (
  auth_user_id,
  full_name,
  email,
  phone,
  avatar_path,
  primary_role,
  status,
  created_by,
  created_at,
  updated_at,
  activated_at
)
select
  p.id,
  p.full_name,
  coalesce(nullif(trim(p.email), ''), p.id::text || '@admin.local'),
  p.phone,
  p.avatar_path,
  'ADMIN'::public.app_role,
  'ACTIVE',
  p.id,
  coalesce(p.created_at, now()),
  coalesce(p.updated_at, now()),
  coalesce(p.updated_at, now())
from public.profiles p
join public.user_roles ur on ur.user_id = p.id
where ur.role = 'ADMIN'::public.app_role
  and not exists (
    select 1
    from public.staff_profiles existing
    where existing.auth_user_id = p.id
  )
on conflict do nothing;
