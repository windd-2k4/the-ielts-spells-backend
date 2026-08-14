package com.theieltsspells.identity.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface StudentDirectoryProjection {
    UUID getId();
    String getStudentCode();
    String getFullName();
    String getEmail();
    String getPhone();
    String getAvatarPath();
    BigDecimal getCurrentBand();
    BigDecimal getTargetBand();
    Boolean getActive();
    String getLifecycleStatus();
    UUID getCurrentCourseId();
    String getCurrentCourseCode();
    String getCurrentCourseName();
    Long getEnrollmentCount();
    LocalDate getJoinedAt();
}
