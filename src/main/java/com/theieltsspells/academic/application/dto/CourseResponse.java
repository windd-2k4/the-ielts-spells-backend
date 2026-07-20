package com.theieltsspells.academic.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CourseResponse(
        UUID id, String code, String name, String description, String level,
        BigDecimal targetBand, Short totalSessions, BigDecimal tuitionAmount,
        Boolean isPublic, Boolean isActive, UUID createdBy,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
) {}
