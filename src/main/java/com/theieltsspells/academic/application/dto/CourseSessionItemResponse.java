package com.theieltsspells.academic.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CourseSessionItemResponse(
        UUID id,
        String itemType,
        String title,
        String description,
        UUID sourceAssignmentId,
        UUID sourceTestId,
        UUID sourceResourceId,
        UUID sourceExerciseTemplateId,
        OffsetDateTime deadlineAt,
        Short displayOrder,
        boolean required,
        String visibility
) {}
