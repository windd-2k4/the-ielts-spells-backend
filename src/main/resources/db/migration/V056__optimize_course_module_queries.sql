create index if not exists idx_course_sessions_course_session_no
  on public.course_sessions(course_id, session_no);

create index if not exists idx_course_activities_course_session_published
  on public.course_activities(course_id, session_id, is_published);
