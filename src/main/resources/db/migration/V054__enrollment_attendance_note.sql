alter table public.enrollments
    add column if not exists attendance_note text;

comment on column public.enrollments.attendance_note is
    'Ghi chú vận hành điểm danh áp dụng cho học viên trong toàn bộ khóa học.';
