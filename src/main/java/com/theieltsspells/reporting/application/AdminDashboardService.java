package com.theieltsspells.reporting.application;

import com.theieltsspells.reporting.application.dto.AdminDashboardResponse;
import com.theieltsspells.shared.persistence.enums.ClassStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final JdbcTemplate jdbc;

    public AdminDashboardResponse overview() {
        var rows = jdbc.query("""
                with normalized_courses as (
                  select course.*,
                    case
                      when course.status = 'PLANNED' then 'OPEN'::public.class_status
                      when course.status = 'OPEN' and course.starts_on <= current_date
                        then 'ACTIVE'::public.class_status
                      else course.status
                    end effective_status
                  from public.courses course
                ), metrics as (
                  select
                    (select count(*) from public.enrollments where status = 'ACTIVE') active_enrollments,
                    count(*) filter (
                      where is_active = true and effective_status in ('OPEN', 'ACTIVE')
                    ) open_courses,
                    count(*) filter (where is_active = true) active_courses,
                    count(*) total_courses
                  from normalized_courses
                ), upcoming as (
                  select id, code, name, capacity, starts_on, effective_status
                  from normalized_courses
                  where is_active = true
                    and effective_status not in ('COMPLETED', 'CANCELLED')
                    and starts_on >= current_date
                  order by starts_on, code
                  limit 3
                )
                select metrics.active_enrollments, metrics.open_courses,
                  metrics.active_courses, metrics.total_courses,
                  upcoming.id, upcoming.code, upcoming.name, upcoming.capacity,
                  upcoming.starts_on, upcoming.effective_status
                from metrics
                left join upcoming on true
                order by upcoming.starts_on, upcoming.code
                """, (rs, ignored) -> new DashboardRow(
                rs.getLong("active_enrollments"),
                rs.getLong("open_courses"),
                rs.getLong("active_courses"),
                rs.getLong("total_courses"),
                rs.getObject("id", java.util.UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getObject("capacity", Short.class),
                rs.getObject("starts_on", java.time.LocalDate.class),
                rs.getString("effective_status")
        ));

        var first = rows.getFirst();
        var upcoming = new ArrayList<AdminDashboardResponse.UpcomingCourse>();
        for (var row : rows) {
            if (row.id() == null) continue;
            upcoming.add(new AdminDashboardResponse.UpcomingCourse(
                    row.id(), row.code(), row.name(), row.capacity(), row.startsOn(),
                    ClassStatus.valueOf(row.status())
            ));
        }
        return new AdminDashboardResponse(
                first.activeEnrollments(), first.openCourses(), first.activeCourses(), first.totalCourses(), upcoming
        );
    }

    private record DashboardRow(
            long activeEnrollments,
            long openCourses,
            long activeCourses,
            long totalCourses,
            java.util.UUID id,
            String code,
            String name,
            Short capacity,
            java.time.LocalDate startsOn,
            String status
    ) {
    }
}
