-- Standard self-study steps shown after the assigned exercise is completed.
insert into public.activity_checklist_items (activity_id, label, description, display_order, is_required)
select a.id, item.label, item.description, item.display_order, true
from public.learning_activities a
cross join (values
  ('Dịch bài', 'Dịch lại nội dung để kiểm tra độ hiểu chính xác.', 1::smallint),
  ('Học từ vựng mới + Làm test từ vựng', 'Lưu từ mới và hoàn thành bài kiểm tra từ vựng.', 2::smallint),
  ('Đọc hiểu không cần từ vựng', 'Đọc lại và tự kiểm tra khả năng hiểu khi không tra từ.', 3::smallint)
) as item(label, description, display_order)
where a.skill in ('LISTENING', 'READING')
  and not exists (
    select 1 from public.activity_checklist_items existing
    where existing.activity_id = a.id
  );
