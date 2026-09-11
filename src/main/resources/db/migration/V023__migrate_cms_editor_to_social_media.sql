-- V022 commits the enum values before this migration uses SOCIAL_MEDIA.
-- CMS_EDITOR has a direct equivalent in the new operating model.
-- MANAGER and TEACHING_ASSISTANT remain unchanged until each account is
-- reviewed and assigned to ADMIN/STUDENT_SUPPORT or TEACHER/STUDENT_SUPPORT.

update public.user_roles
set role = 'SOCIAL_MEDIA'
where role = 'CMS_EDITOR';

update public.staff_profiles
set primary_role = 'SOCIAL_MEDIA', updated_at = now()
where primary_role = 'CMS_EDITOR';

update public.staff_invitations
set intended_role = 'SOCIAL_MEDIA', updated_at = now()
where intended_role = 'CMS_EDITOR';
