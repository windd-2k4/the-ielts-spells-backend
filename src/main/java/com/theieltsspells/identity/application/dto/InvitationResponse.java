package com.theieltsspells.identity.application.dto;

import com.theieltsspells.identity.domain.InvitationStatus;
import com.theieltsspells.shared.persistence.enums.AppRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationResponse(UUID id, UUID staffProfileId, String email, String fullName,
                                 AppRole intendedRole, InvitationStatus status,
                                 OffsetDateTime invitedAt, OffsetDateTime expiresAt,
                                 OffsetDateTime acceptedAt) {}
