package com.theieltsspells.studentportal.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StudentCourseResponse(
        UUID id,
        String code,
        String name,
        String description,
        String level,
        String skillPair,
        BigDecimal targetBand,
        Short totalSessions,
        BigDecimal tuitionAmount,
        Short capacity,
        LocalDate startsOn,
        LocalDate endsOn,
        String status,
        String defaultZoomUrl
) {
}
