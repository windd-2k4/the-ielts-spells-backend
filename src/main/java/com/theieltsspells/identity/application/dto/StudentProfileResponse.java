package com.theieltsspells.identity.application.dto;

public record StudentProfileResponse(
        String fullName,
        String phone,
        String avatarPath
) {}
