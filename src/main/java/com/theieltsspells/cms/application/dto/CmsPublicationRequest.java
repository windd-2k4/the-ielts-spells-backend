package com.theieltsspells.cms.application.dto;

import com.theieltsspells.shared.persistence.enums.PublishStatus;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CmsPublicationRequest(@NotNull PublishStatus status, OffsetDateTime publishedAt) {
}
