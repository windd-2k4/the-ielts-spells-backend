package com.theieltsspells.reporting.application.dto;

import com.theieltsspells.shared.persistence.enums.ClassStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminDashboardResponse(
        long activeEnrollments,
        long openCourses,
        long activeCourses,
        long totalCourses,
        List<UpcomingCourse> upcomingCourses
) {
    public record UpcomingCourse(
            UUID id,
            String code,
            String name,
            short capacity,
            LocalDate startsOn,
            ClassStatus status
    ) {
    }
}
