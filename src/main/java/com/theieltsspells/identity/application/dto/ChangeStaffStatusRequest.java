package com.theieltsspells.identity.application.dto;

import com.theieltsspells.identity.domain.StaffStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStaffStatusRequest(@NotNull StaffStatus status) {}
