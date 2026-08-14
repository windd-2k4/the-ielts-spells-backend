alter table public.enrollments
  add column if not exists planned_exam_month date,
  add column if not exists actual_exam_date date,
  add column if not exists exam_registration_status varchar(20) not null default 'NOT_REGISTERED',
  add column if not exists target_note varchar(255);

alter table public.enrollments
  drop constraint if exists ck_enrollments_exam_registration_status;

alter table public.enrollments
  add constraint ck_enrollments_exam_registration_status
  check (exam_registration_status in ('NOT_REGISTERED', 'REGISTERED', 'ISSUE'));

comment on column public.enrollments.planned_exam_month is
  'Tháng học viên dự kiến thi IELTS; luôn lưu ngày đầu tháng.';
comment on column public.enrollments.actual_exam_date is
  'Ngày thi IELTS thực tế nếu đã chốt lịch.';
comment on column public.enrollments.exam_registration_status is
  'NOT_REGISTERED, REGISTERED hoặc ISSUE.';
comment on column public.enrollments.target_note is
  'Mục tiêu thi của học viên trong phạm vi lượt ghi danh này.';
