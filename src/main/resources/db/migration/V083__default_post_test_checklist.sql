-- Assigned online tests share the same three post-test steps used by the course report.

create or replace function public.ensure_default_test_activity_checklist(p_activity_id uuid)
returns void
language sql
as $$
  insert into public.activity_checklist_items(
    activity_id, label, description, display_order, is_required
  ) values
    (p_activity_id, 'Dịch bài', 'Đọc lại và dịch các đoạn cần củng cố.', 0, true),
    (p_activity_id, 'Từ vựng + test', 'Ôn từ mới và kiểm tra lại từ vựng.', 1, true),
    (p_activity_id, 'Đọc hiểu không cần từ vựng', 'Làm lại bài khi không còn phụ thuộc danh sách từ.', 2, true)
  on conflict (activity_id, display_order) do nothing;
$$;

create or replace function public.ensure_default_test_activity_checklist_trigger()
returns trigger
language plpgsql
as $$
begin
  if new.test_assignment_id is not null then
    perform public.ensure_default_test_activity_checklist(new.activity_id);
  end if;
  return new;
end;
$$;

drop trigger if exists trg_course_activities_test_checklist on public.course_activities;
create trigger trg_course_activities_test_checklist
  after insert or update of test_assignment_id on public.course_activities
  for each row execute function public.ensure_default_test_activity_checklist_trigger();

select public.ensure_default_test_activity_checklist(activity_id)
from public.course_activities
where test_assignment_id is not null;
