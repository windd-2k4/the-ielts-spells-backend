-- This table is for a prospective staff member, not for learner registration
-- or administrator self-provisioning. V006 already converted enum labels to
-- uppercase, so the replacement constraint must use uppercase labels too.

alter table public.access_requests
  drop constraint if exists access_requests_management_role_check;

-- CMS_EDITOR has a direct replacement. MANAGER and TEACHING_ASSISTANT stay
-- temporarily valid so historical requests remain reviewable until manually
-- mapped to a target role.
update public.access_requests
set requested_role = 'SOCIAL_MEDIA'
where requested_role = 'CMS_EDITOR';

alter table public.access_requests
  add constraint access_requests_management_role_check
  check (requested_role in (
    'ADMISSIONS', 'SOCIAL_MEDIA', 'TEACHER', 'STUDENT_SUPPORT',
    'MANAGER', 'TEACHING_ASSISTANT'
  ));
