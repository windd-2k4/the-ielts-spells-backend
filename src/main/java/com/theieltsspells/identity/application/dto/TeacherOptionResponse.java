package com.theieltsspells.identity.application.dto;

import com.theieltsspells.shared.persistence.enums.AppRole;

import java.util.UUID;

public record TeacherOptionResponse(UUID id, String fullName, String email, AppRole role) {}
