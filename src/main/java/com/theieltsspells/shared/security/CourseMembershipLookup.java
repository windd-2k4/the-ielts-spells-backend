package com.theieltsspells.shared.security;

import java.util.UUID;

/**
 * Security-facing port for resolving course-scoped memberships.
 *
 * <p>The shared authorization policy owns this contract while the academic
 * module supplies the database-backed implementation. This keeps shared
 * infrastructure independent from a business module.</p>
 */
public interface CourseMembershipLookup {

    boolean isTeacherAssigned(UUID courseId, UUID teacherId);

    boolean isStudentSupportAssigned(UUID courseId, UUID studentSupportId);

    boolean hasActiveEnrollment(UUID courseId, UUID studentId);
}
