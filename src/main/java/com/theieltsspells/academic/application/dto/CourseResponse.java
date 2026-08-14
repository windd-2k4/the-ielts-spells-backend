package com.theieltsspells.academic.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.time.LocalDate;
import com.theieltsspells.shared.persistence.enums.ClassStatus;
import com.theieltsspells.shared.persistence.enums.SkillPair;

public record CourseResponse(
        UUID id, UUID programId, String code, String name, String description, String level, SkillPair skillPair,
        BigDecimal targetBand, Short totalSessions, BigDecimal tuitionAmount,
        Short capacity, LocalDate startsOn, LocalDate endsOn, ClassStatus status, String defaultZoomUrl,
        Boolean isPublic, Boolean isActive, UUID createdBy,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
) {}
