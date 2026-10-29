-- Timed notification jobs run every minute; these keys make each reminder idempotent.
create unique index if not exists uq_notifications_upcoming_session
  on public.notifications(user_id, type, ((data->>'sessionId')))
  where type = 'SESSION_UPCOMING';

create unique index if not exists uq_notifications_assignment_due
  on public.notifications(user_id, type, ((data->>'assignmentId')), ((data->>'effectiveDueAt')))
  where type = 'ASSIGNMENT_DUE';

create unique index if not exists uq_notifications_weekly_digest
  on public.notifications(user_id, type, ((data->>'weekStart')))
  where type = 'WEEKLY_PROGRESS_DIGEST';

-- Writing and Speaking use IELTS bands. Preserve values already expressed as a
-- band and convert only legacy percentage-style values above band 9.
update public.teacher_activity_reviews review
set verified_score = least(9, round((review.verified_score /
    coalesce(nullif(assignment.max_score, 0), 100)) * 18) / 2)
from public.student_activity_attempts attempt
join public.course_activities activity on activity.id = attempt.class_activity_id
join public.learning_activities learning_activity on learning_activity.id = activity.activity_id
join public.assignments assignment on assignment.id = activity.assignment_id
where review.attempt_id = attempt.id
  and learning_activity.skill in ('WRITING', 'SPEAKING')
  and review.verified_score > 9;

update public.submissions submission
set score = least(9, round((submission.score /
    coalesce(nullif(assignment.max_score, 0), 100)) * 18) / 2)
from public.assignments assignment
join public.course_activities activity on activity.assignment_id = assignment.id
join public.learning_activities learning_activity on learning_activity.id = activity.activity_id
where submission.assignment_id = assignment.id
  and learning_activity.skill in ('WRITING', 'SPEAKING')
  and submission.score > 9;

update public.student_activity_attempts attempt
set score = case
      when attempt.score > 9 then least(9,
        round((attempt.score / coalesce(nullif(attempt.max_score, 0),
          nullif(assignment.max_score, 0), 100)) * 18) / 2)
      else attempt.score
    end,
    max_score = 9,
    updated_at = now()
from public.course_activities activity
join public.learning_activities learning_activity on learning_activity.id = activity.activity_id
join public.assignments assignment on assignment.id = activity.assignment_id
where attempt.class_activity_id = activity.id
  and learning_activity.skill in ('WRITING', 'SPEAKING');

update public.assignments assignment
set max_score = 9,
    updated_at = now()
from public.course_activities activity
join public.learning_activities learning_activity on learning_activity.id = activity.activity_id
where activity.assignment_id = assignment.id
  and learning_activity.skill in ('WRITING', 'SPEAKING');

update public.course_activities activity
set max_score_override = 9,
    updated_at = now()
from public.learning_activities learning_activity
where learning_activity.id = activity.activity_id
  and learning_activity.skill in ('WRITING', 'SPEAKING')
  and activity.max_score_override is not null;

update public.learning_activities
set default_max_score = 9,
    updated_at = now()
where skill in ('WRITING', 'SPEAKING')
  and default_max_score is not null;
