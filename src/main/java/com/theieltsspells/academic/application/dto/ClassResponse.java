package com.theieltsspells.academic.application.dto;

import com.theieltsspells.shared.persistence.enums.ClassStatus;
import java.time.*;
import java.util.UUID;

public record ClassResponse(
        UUID id, UUID courseId, String code, String name, Short capacity,
        LocalDate startsOn, LocalDate endsOn, ClassStatus status,
        String defaultZoomUrl, UUID createdBy,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
) {}
