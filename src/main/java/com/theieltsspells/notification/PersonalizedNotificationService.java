package com.theieltsspells.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalizedNotificationService {

    public record StudyScheduleDto(
            boolean dailyReminderEnabled,
            String preferredStudyTime,
            boolean streakSaverEnabled,
            boolean pvpChallengeAlert,
            boolean srsDueAlert
    ) {}

    public record FeedNotice(
            UUID id,
            String type,
            String title,
            String body,
            String mascotType,
            String targetAction,
            String targetId,
            OffsetDateTime createdAt,
            OffsetDateTime readAt
    ) {}

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<FeedNotice> getFeed(UUID studentId, boolean unreadOnly) {
        String filter = unreadOnly ? " and n.read_at is null " : "";
        return jdbc.query("""
                select n.id, n.type, n.title, n.body,
                       coalesce(n.data->>'mascotType', scp.mascot_type, 'OWL') as mascot_type,
                       coalesce(n.data->>'targetAction', '') as target_action,
                       coalesce(n.data->>'challengeId', n.data->>'friendshipId', '') as target_id,
                       n.created_at, n.read_at
                from public.notifications n
                left join public.student_companion_profiles scp on scp.student_id = n.user_id
                where n.user_id = ?
                """ + filter + """
                order by n.created_at desc
                limit 40
                """,
                (rs, rowNum) -> new FeedNotice(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getString("mascot_type"),
                        rs.getString("target_action"),
                        rs.getString("target_id"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("read_at", OffsetDateTime.class)
                ),
                studentId);
    }

    @Transactional(readOnly = true)
    public int unreadCount(UUID studentId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from public.notifications where user_id = ? and read_at is null",
                Integer.class, studentId);
        return count == null ? 0 : count;
    }

    @Transactional
    public void markRead(UUID studentId, UUID notificationId) {
        jdbc.update("update public.notifications set read_at = now() where id = ? and user_id = ? and read_at is null",
                notificationId, studentId);
    }

    @Transactional
    public void markAllRead(UUID studentId) {
        jdbc.update("update public.notifications set read_at = now() where user_id = ? and read_at is null",
                studentId);
    }

    @Transactional(readOnly = true)
    public StudyScheduleDto getSchedule(UUID studentId) {
        List<StudyScheduleDto> rows = jdbc.query("""
                select daily_reminder_enabled, preferred_study_time,
                       streak_saver_enabled, pvp_challenge_alert, srs_due_alert
                from public.student_study_schedules
                where student_id = ?
                """,
                (rs, rowNum) -> new StudyScheduleDto(
                        rs.getBoolean("daily_reminder_enabled"),
                        rs.getString("preferred_study_time"),
                        rs.getBoolean("streak_saver_enabled"),
                        rs.getBoolean("pvp_challenge_alert"),
                        rs.getBoolean("srs_due_alert")
                ),
                studentId);
        if (rows.isEmpty()) {
            return new StudyScheduleDto(true, "20:00", true, true, true);
        }
        return rows.getFirst();
    }

    @Transactional
    public StudyScheduleDto saveSchedule(UUID studentId, StudyScheduleDto req) {
        String cleanTime = normalizeTime(req.preferredStudyTime());
        jdbc.update("""
                insert into public.student_study_schedules
                  (student_id, daily_reminder_enabled, preferred_study_time,
                   streak_saver_enabled, pvp_challenge_alert, srs_due_alert, updated_at)
                values (?, ?, ?, ?, ?, ?, now())
                on conflict (student_id) do update
                set daily_reminder_enabled = excluded.daily_reminder_enabled,
                    preferred_study_time = excluded.preferred_study_time,
                    streak_saver_enabled = excluded.streak_saver_enabled,
                    pvp_challenge_alert = excluded.pvp_challenge_alert,
                    srs_due_alert = excluded.srs_due_alert,
                    updated_at = now()
                """,
                studentId,
                req.dailyReminderEnabled(),
                cleanTime,
                req.streakSaverEnabled(),
                req.pvpChallengeAlert(),
                req.srsDueAlert());

        jdbc.update("""
                update public.student_companion_profiles
                set preferred_study_time = ?, updated_at = now()
                where student_id = ?
                """, cleanTime, studentId);

        return getSchedule(studentId);
    }

    @Transactional
    public void sendDirectNotice(UUID userId, String type, String title, String body, String jsonData) {
        jdbc.update("""
                insert into public.notifications(user_id, type, title, body, data)
                values (?, ?, ?, ?, ?::jsonb)
                """, userId, type, title, body, jsonData);
    }

    /**
     * Runs every 5 minutes (VN timezone) to deliver personalized daily study reminders
     * and Streak Saver alerts while enforcing quiet hours (22:30-07:30) and max 2 study reminders/day.
     */
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void dispatchPersonalizedReminders() {
        LocalTime now = LocalTime.now(VN_ZONE);
        if (now.isAfter(LocalTime.of(22, 30)) || now.isBefore(LocalTime.of(7, 30))) {
            return;
        }
        String todayKey = LocalDate.now(VN_ZONE).toString();
        String currentHhMm = now.format(DateTimeFormatter.ofPattern("HH:mm"));
        String windowStart = now.minusMinutes(10).format(DateTimeFormatter.ofPattern("HH:mm"));

        // 1. Preferred Study Time Reminder
        jdbc.update("""
                insert into public.notifications(user_id, type, title, body, data)
                select scp.student_id, 'DAILY_STUDY_REMINDER',
                  case when scp.mascot_type = 'DRAGON'
                    then 'Ignis đã sẵn sàng cùng bạn!'
                    else 'Spellie đang đợi bạn vào bài!'
                  end,
                  case when scp.mascot_type = 'DRAGON'
                    then 'Đã tới giờ học ' || coalesce(sched.preferred_study_time, scp.preferred_study_time, '20:00')
                         || ': Hoàn thành Lộ trình hôm nay để nhận +10 💎 mỗi chặng nhé!'
                    else 'Tới giờ vàng ' || coalesce(sched.preferred_study_time, scp.preferred_study_time, '20:00')
                         || ' rồi! Dành 5 phút đọc truyện & nói cùng Spellie nhé.'
                  end,
                  jsonb_build_object('mascotType', scp.mascot_type, 'targetAction', 'DAILY_ROUTINE', 'date', ?)
                from public.student_companion_profiles scp
                left join public.student_study_schedules sched on sched.student_id = scp.student_id
                left join public.student_wallets w on w.student_id = scp.student_id
                where scp.onboarding_completed = true
                  and coalesce(sched.daily_reminder_enabled, true) = true
                  and coalesce(sched.preferred_study_time, scp.preferred_study_time, '20:00') between ? and ?
                  and coalesce(jsonb_array_length(w.daily_routine_claims -> ?), 0) = 0
                  and (
                    select count(*) from public.notifications n
                    where n.user_id = scp.student_id
                      and n.type in ('DAILY_STUDY_REMINDER', 'STREAK_SAVER')
                      and n.created_at >= (current_date at time zone 'Asia/Ho_Chi_Minh')
                  ) < 2
                  and not exists (
                    select 1 from public.notifications n2
                    where n2.user_id = scp.student_id
                      and n2.type = 'DAILY_STUDY_REMINDER'
                      and n2.data->>'date' = ?
                  )
                """, todayKey, windowStart, currentHhMm, todayKey, todayKey);

        // 2. Streak Saver Alert at 21:30 for learners with streak >= 2 who have not finished today
        if (!now.isBefore(LocalTime.of(21, 30)) && now.isBefore(LocalTime.of(21, 45))) {
            jdbc.update("""
                    insert into public.notifications(user_id, type, title, body, data)
                    select w.student_id, 'STREAK_SAVER',
                      '🔥 Cẩn thận mất chuỗi ' || w.streak_days || ' ngày!',
                      'Chỉ còn ít giờ nữa là hết ngày — làm nhanh 1 ván Mini-Game để giữ vững ngọn lửa Streak và nhận Kim cương nhé!',
                      jsonb_build_object('mascotType', coalesce(scp.mascot_type, 'OWL'), 'targetAction', 'DAILY_ROUTINE', 'date', ?)
                    from public.student_wallets w
                    left join public.student_companion_profiles scp on scp.student_id = w.student_id
                    left join public.student_study_schedules sched on sched.student_id = w.student_id
                    where w.streak_days >= 2
                      and coalesce(sched.streak_saver_enabled, true) = true
                      and (w.last_active_date is null or w.last_active_date < current_date)
                      and coalesce(jsonb_array_length(w.daily_routine_claims -> ?), 0) = 0
                      and not exists (
                        select 1 from public.notifications n2
                        where n2.user_id = w.student_id
                          and n2.type = 'STREAK_SAVER'
                          and n2.data->>'date' = ?
                      )
                    """, todayKey, todayKey, todayKey);
        }
    }

    private static String normalizeTime(String raw) {
        if (raw == null || !raw.matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) {
            return "20:00";
        }
        return raw.length() == 4 ? "0" + raw : raw;
    }
}
