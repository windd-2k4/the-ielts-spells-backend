package com.theieltsspells.academic.application;

import java.util.UUID;

public record AttendanceRosterMember(
        UUID studentId,
        String studentCode,
        String fullName,
        String email,
        String avatarPath
) {}
