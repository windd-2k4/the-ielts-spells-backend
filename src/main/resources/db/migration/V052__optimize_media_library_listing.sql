-- The media list filters active files and sorts newest first.
create index if not exists idx_learning_resource_files_active_created
  on public.learning_resource_files(created_at desc, id desc)
  where archived_at is null;
