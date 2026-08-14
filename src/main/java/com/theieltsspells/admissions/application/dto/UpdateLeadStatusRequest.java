package com.theieltsspells.admissions.application.dto;

import com.theieltsspells.shared.persistence.enums.LeadStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateLeadStatusRequest(@NotNull LeadStatus status) {
}
