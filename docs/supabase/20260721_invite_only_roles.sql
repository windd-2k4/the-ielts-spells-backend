-- Run once in Supabase SQL Editor. The current cloud project still has the
-- legacy app_role enum ('user', 'admin'), while the application uses these
-- staff roles during account activation.
alter type public.app_role add value if not exists 'manager';
alter type public.app_role add value if not exists 'teacher';
alter type public.app_role add value if not exists 'teaching_assistant';
alter type public.app_role add value if not exists 'admissions';
alter type public.app_role add value if not exists 'cms_editor';
alter type public.app_role add value if not exists 'student';

notify pgrst, 'reload schema';
