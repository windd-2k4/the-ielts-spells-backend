package com.theieltsspells.learninglibrary.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LibraryFolderResponse(
        UUID id,
        String name,
        UUID courseId,
        String courseName,
        long itemCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
