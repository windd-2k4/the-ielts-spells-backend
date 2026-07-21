package com.theieltsspells.identity.application.dto;

import com.theieltsspells.shared.persistence.enums.AppRole;
import jakarta.validation.constraints.NotNull;

public record ChangeStaffRoleRequest(@NotNull AppRole role) {}
