-- Hibernate's @Enumerated(EnumType.STRING) persists Java enum constant names.
-- PostgreSQL enum labels therefore need to match those uppercase names exactly.

alter type public.app_role rename value 'admin' to 'ADMIN';
alter type public.app_role rename value 'manager' to 'MANAGER';
alter type public.app_role rename value 'cms_editor' to 'CMS_EDITOR';
alter type public.app_role rename value 'admissions' to 'ADMISSIONS';
alter type public.app_role rename value 'teacher' to 'TEACHER';
alter type public.app_role rename value 'teaching_assistant' to 'TEACHING_ASSISTANT';
alter type public.app_role rename value 'student' to 'STUDENT';

alter type public.publish_status rename value 'draft' to 'DRAFT';
alter type public.publish_status rename value 'scheduled' to 'SCHEDULED';
alter type public.publish_status rename value 'published' to 'PUBLISHED';
alter type public.publish_status rename value 'archived' to 'ARCHIVED';

alter type public.lead_status rename value 'new' to 'NEW';
alter type public.lead_status rename value 'contacted' to 'CONTACTED';
alter type public.lead_status rename value 'qualified' to 'QUALIFIED';
alter type public.lead_status rename value 'converted' to 'CONVERTED';
alter type public.lead_status rename value 'lost' to 'LOST';

alter type public.class_status rename value 'planned' to 'PLANNED';
alter type public.class_status rename value 'open' to 'OPEN';
alter type public.class_status rename value 'active' to 'ACTIVE';
alter type public.class_status rename value 'completed' to 'COMPLETED';
alter type public.class_status rename value 'cancelled' to 'CANCELLED';

alter type public.enrollment_status rename value 'pending' to 'PENDING';
alter type public.enrollment_status rename value 'active' to 'ACTIVE';
alter type public.enrollment_status rename value 'paused' to 'PAUSED';
alter type public.enrollment_status rename value 'completed' to 'COMPLETED';
alter type public.enrollment_status rename value 'withdrawn' to 'WITHDRAWN';

alter type public.session_status rename value 'scheduled' to 'SCHEDULED';
alter type public.session_status rename value 'completed' to 'COMPLETED';
alter type public.session_status rename value 'cancelled' to 'CANCELLED';

alter type public.attendance_status rename value 'present' to 'PRESENT';
alter type public.attendance_status rename value 'late' to 'LATE';
alter type public.attendance_status rename value 'left_early' to 'LEFT_EARLY';
alter type public.attendance_status rename value 'absent' to 'ABSENT';
alter type public.attendance_status rename value 'excused' to 'EXCUSED';

alter type public.assignment_status rename value 'draft' to 'DRAFT';
alter type public.assignment_status rename value 'published' to 'PUBLISHED';
alter type public.assignment_status rename value 'closed' to 'CLOSED';
alter type public.assignment_status rename value 'archived' to 'ARCHIVED';

alter type public.submission_status rename value 'draft' to 'DRAFT';
alter type public.submission_status rename value 'submitted' to 'SUBMITTED';
alter type public.submission_status rename value 'late' to 'LATE';
alter type public.submission_status rename value 'returned' to 'RETURNED';
alter type public.submission_status rename value 'graded' to 'GRADED';

alter type public.question_type rename value 'single_choice' to 'SINGLE_CHOICE';
alter type public.question_type rename value 'multiple_choice' to 'MULTIPLE_CHOICE';
alter type public.question_type rename value 'short_text' to 'SHORT_TEXT';
alter type public.question_type rename value 'long_text' to 'LONG_TEXT';
alter type public.question_type rename value 'writing' to 'WRITING';

alter type public.attempt_status rename value 'in_progress' to 'IN_PROGRESS';
alter type public.attempt_status rename value 'submitted' to 'SUBMITTED';
alter type public.attempt_status rename value 'graded' to 'GRADED';
alter type public.attempt_status rename value 'expired' to 'EXPIRED';

alter type public.evaluation_status rename value 'queued' to 'QUEUED';
alter type public.evaluation_status rename value 'processing' to 'PROCESSING';
alter type public.evaluation_status rename value 'ai_completed' to 'AI_COMPLETED';
alter type public.evaluation_status rename value 'teacher_reviewed' to 'TEACHER_REVIEWED';
alter type public.evaluation_status rename value 'published' to 'PUBLISHED';
alter type public.evaluation_status rename value 'failed' to 'FAILED';

alter type public.job_status rename value 'pending' to 'PENDING';
alter type public.job_status rename value 'processing' to 'PROCESSING';
alter type public.job_status rename value 'completed' to 'COMPLETED';
alter type public.job_status rename value 'failed' to 'FAILED';
alter type public.job_status rename value 'cancelled' to 'CANCELLED';

alter type public.skill_type rename value 'listening' to 'LISTENING';
alter type public.skill_type rename value 'reading' to 'READING';
alter type public.skill_type rename value 'writing' to 'WRITING';
alter type public.skill_type rename value 'speaking' to 'SPEAKING';
alter type public.skill_type rename value 'vocabulary' to 'VOCABULARY';
alter type public.skill_type rename value 'general' to 'GENERAL';

alter type public.activity_type rename value 'online_test' to 'ONLINE_TEST';
alter type public.activity_type rename value 'manual_result' to 'MANUAL_RESULT';
alter type public.activity_type rename value 'writing_submission' to 'WRITING_SUBMISSION';
alter type public.activity_type rename value 'audio_submission' to 'AUDIO_SUBMISSION';
alter type public.activity_type rename value 'file_submission' to 'FILE_SUBMISSION';
alter type public.activity_type rename value 'vocabulary_list' to 'VOCABULARY_LIST';
alter type public.activity_type rename value 'checklist' to 'CHECKLIST';
alter type public.activity_type rename value 'external_link' to 'EXTERNAL_LINK';

alter type public.completion_method rename value 'web_only' to 'WEB_ONLY';
alter type public.completion_method rename value 'manual_only' to 'MANUAL_ONLY';
alter type public.completion_method rename value 'web_or_manual' to 'WEB_OR_MANUAL';

alter type public.activity_attempt_status rename value 'not_started' to 'NOT_STARTED';
alter type public.activity_attempt_status rename value 'in_progress' to 'IN_PROGRESS';
alter type public.activity_attempt_status rename value 'submitted' to 'SUBMITTED';
alter type public.activity_attempt_status rename value 'late' to 'LATE';
alter type public.activity_attempt_status rename value 'waiting_review' to 'WAITING_REVIEW';
alter type public.activity_attempt_status rename value 'needs_revision' to 'NEEDS_REVISION';
alter type public.activity_attempt_status rename value 'completed' to 'COMPLETED';
alter type public.activity_attempt_status rename value 'cancelled' to 'CANCELLED';

alter type public.result_source rename value 'web' to 'WEB';
alter type public.result_source rename value 'student_manual' to 'STUDENT_MANUAL';
alter type public.result_source rename value 'teacher_manual' to 'TEACHER_MANUAL';
alter type public.result_source rename value 'imported' to 'IMPORTED';

alter type public.review_status rename value 'not_required' to 'NOT_REQUIRED';
alter type public.review_status rename value 'pending' to 'PENDING';
alter type public.review_status rename value 'verified' to 'VERIFIED';
alter type public.review_status rename value 'revision_requested' to 'REVISION_REQUESTED';
alter type public.review_status rename value 'rejected' to 'REJECTED';
