-- Target staff roles are ADMIN, ADMISSIONS, SOCIAL_MEDIA, TEACHER and
-- STUDENT_SUPPORT. STUDENT remains the learner role. PostgreSQL enum labels
-- are uppercase because V006 aligned them with the Java enum names.

alter type public.app_role add value if not exists 'SOCIAL_MEDIA';
alter type public.app_role add value if not exists 'STUDENT_SUPPORT';
