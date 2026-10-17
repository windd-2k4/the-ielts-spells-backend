package com.theieltsspells.progress.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ActivityEvidenceResponse(UUID id, String evidenceType, String originalName,
        OffsetDateTime uploadedAt) {}
