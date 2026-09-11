package com.theieltsspells.shared.security;

import com.theieltsspells.shared.persistence.enums.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central authorization policy for the application role matrix.
 *
 * <p>Controllers call this bean from {@code @PreAuthorize}; business services
 * still enforce ownership and lifecycle invariants before mutating data.</p>
 */
@Component("permissionPolicy")
@RequiredArgsConstructor
public class PermissionPolicy {

    private static final Set<ApplicationPermission> ALL = Set.copyOf(EnumSet.allOf(ApplicationPermission.class));

    private static final Map<AppRole, Set<ApplicationPermission>> GRANTS = grants();

    private final CourseMembershipLookup memberships;

    public boolean has(Authentication authentication, String permissionKey) {
        return ApplicationPermission.fromKey(permissionKey)
                .map(permission -> has(authentication, permission))
                .orElse(false);
    }

    public boolean has(Authentication authentication, ApplicationPermission permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return roles(authentication).stream()
                .map(GRANTS::get)
                .filter(values -> values != null)
                .anyMatch(values -> values.contains(permission));
    }

    /**
     * Authorizes a non-course-specific back-office endpoint. A learner or
     * teacher can hold the underlying read permission for their own data but
     * must not use an unscoped administrative listing endpoint.
     */
    public boolean hasAdministrativeScope(Authentication authentication, String permissionKey) {
        var permission = ApplicationPermission.fromKey(permissionKey).orElse(null);
        if (permission == null || !has(authentication, permission)) {
            return false;
        }
        var roles = roles(authentication);
        return roles.contains(AppRole.ADMIN)
                || roles.contains(AppRole.ADMISSIONS)
                || roles.contains(AppRole.MANAGER);
    }

    /**
     * The current library API is an authoring/back-office surface. Learner
     * access to published material will use a separate student-facing route.
     */
    public boolean canAccessLibraryAdministration(Authentication authentication) {
        if (!has(authentication, ApplicationPermission.LIBRARY_PUBLISHED_READ)) {
            return false;
        }
        var roles = roles(authentication);
        return roles.contains(AppRole.ADMIN)
                || roles.contains(AppRole.TEACHER)
                || roles.contains(AppRole.MANAGER);
    }

    /**
     * The current test-bank responses contain draft content and answer data.
     * Student Support receives a future published-test selector, not this raw
     * authoring endpoint.
     */
    public boolean canViewTestBank(Authentication authentication) {
        if (!has(authentication, ApplicationPermission.TEST_READ)) {
            return false;
        }
        var roles = roles(authentication);
        return roles.contains(AppRole.ADMIN)
                || roles.contains(AppRole.TEACHER)
                || roles.contains(AppRole.MANAGER);
    }

    /**
     * Authorizes a course-scoped action. Teachers must be assigned to the
     * course, Student Support staff must be assigned to the course, and
     * students must have an active enrollment. Only the permanent
     * operational roles explicitly granted a global scope can bypass a
     * course-membership check.
     */
    public boolean hasForCourse(Authentication authentication, UUID courseId, String permissionKey) {
        var permission = ApplicationPermission.fromKey(permissionKey).orElse(null);
        if (courseId == null || permission == null || !has(authentication, permission)) {
            return false;
        }

        var roles = roles(authentication);
        if (hasGlobalCourseScope(roles, permission)) {
            return true;
        }

        var actorId = subject(authentication);
        if (actorId == null) {
            return false;
        }
        if (roles.contains(AppRole.TEACHER) || roles.contains(AppRole.TEACHING_ASSISTANT)) {
            return memberships.isTeacherAssigned(courseId, actorId);
        }
        if (roles.contains(AppRole.STUDENT_SUPPORT)) {
            return memberships.isStudentSupportAssigned(courseId, actorId);
        }
        if (roles.contains(AppRole.STUDENT)) {
            return memberships.hasActiveEnrollment(courseId, actorId);
        }
        return false;
    }

    /**
     * Dedicated Student Support endpoints must never be usable by a teacher
     * who happens to hold an overlapping read permission.
     */
    public boolean isStudentSupportAssignedToCourse(Authentication authentication, UUID courseId) {
        var actorId = subject(authentication);
        return actorId != null
                && roles(authentication).contains(AppRole.STUDENT_SUPPORT)
                && memberships.isStudentSupportAssigned(courseId, actorId);
    }

    public boolean isStudentSupport(Authentication authentication) {
        return roles(authentication).contains(AppRole.STUDENT_SUPPORT);
    }

    /**
     * Teachers may submit a draft for review; only an administrator can
     * publish. The test service remains responsible for draft ownership and
     * lifecycle transition validation.
     */
    public boolean canChangeTestStatus(Authentication authentication, String requestedStatus) {
        if ("PUBLISHED".equalsIgnoreCase(requestedStatus)) {
            return has(authentication, ApplicationPermission.TEST_PUBLISH);
        }
        return has(authentication, ApplicationPermission.TEST_DRAFT_UPDATE_OWN)
                || has(authentication, ApplicationPermission.TEST_PUBLISH);
    }

    /**
     * Publishing and scheduling CMS content are deliberate release actions;
     * drafts and archival remain ordinary content-management actions.
     */
    public boolean canChangeCmsPublication(Authentication authentication, String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return false;
        }
        if ("PUBLISHED".equalsIgnoreCase(requestedStatus)
                || "SCHEDULED".equalsIgnoreCase(requestedStatus)) {
            return has(authentication, ApplicationPermission.CMS_CONTENT_PUBLISH);
        }
        return has(authentication, ApplicationPermission.CMS_CONTENT_MANAGE);
    }

    /** A test moderator may approve, publish, or bypass test-draft ownership. */
    public boolean isTestModerator(Authentication authentication) {
        return has(authentication, ApplicationPermission.TEST_PUBLISH);
    }

    /**
     * Cross-course test assignment remains an administrative operation.
     * Student Support staff can view the learners assigned to them but do not
     * publish or assign academic tests.
     */
    public boolean canManageTestAssignmentsAcrossCourses(Authentication authentication) {
        return has(authentication, ApplicationPermission.TEST_ASSIGNMENT_MANAGE)
                && hasGlobalCourseScope(roles(authentication), ApplicationPermission.TEST_ASSIGNMENT_MANAGE);
    }

    private static boolean hasGlobalCourseScope(Set<AppRole> roles, ApplicationPermission permission) {
        if (roles.contains(AppRole.ADMIN)
                || roles.contains(AppRole.MANAGER)) {
            return true;
        }
        return roles.contains(AppRole.ADMISSIONS)
                && (permission == ApplicationPermission.COURSE_READ || permission == ApplicationPermission.SESSION_READ);
    }

    private static UUID subject(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Set<AppRole> roles(Authentication authentication) {
        var resolved = EnumSet.noneOf(AppRole.class);
        Collection<? extends org.springframework.security.core.GrantedAuthority> authorities = authentication.getAuthorities();
        for (var authority : authorities) {
            String value = authority.getAuthority();
            for (var role : AppRole.values()) {
                if (role.getDatabaseValue().equalsIgnoreCase(value) || role.name().equalsIgnoreCase(value)) {
                    resolved.add(role);
                }
            }
        }
        return resolved;
    }

    private static Map<AppRole, Set<ApplicationPermission>> grants() {
        var values = new EnumMap<AppRole, Set<ApplicationPermission>>(AppRole.class);
        values.put(AppRole.ADMIN, ALL);
        values.put(AppRole.ADMISSIONS, permissions(
                ApplicationPermission.IDENTITY_PROFILE_SELF_READ, ApplicationPermission.IDENTITY_PROFILE_SELF_UPDATE,
                ApplicationPermission.ADMISSIONS_LEAD_READ, ApplicationPermission.ADMISSIONS_LEAD_MANAGE,
                ApplicationPermission.ADMISSIONS_LEAD_CONVERT, ApplicationPermission.STUDENT_PROFILE_READ,
                ApplicationPermission.ENROLLMENT_READ, ApplicationPermission.ENROLLMENT_CREATE,
                ApplicationPermission.ENROLLMENT_LIFECYCLE_REQUEST, ApplicationPermission.ENROLLMENT_EXAM_PLAN_MANAGE,
                ApplicationPermission.COURSE_READ, ApplicationPermission.SESSION_READ,
                ApplicationPermission.CMS_CONTENT_READ, ApplicationPermission.REPORT_ADMISSIONS_READ));
        values.put(AppRole.SOCIAL_MEDIA, permissions(
                ApplicationPermission.IDENTITY_PROFILE_SELF_READ, ApplicationPermission.IDENTITY_PROFILE_SELF_UPDATE,
                ApplicationPermission.CMS_CONTENT_READ, ApplicationPermission.CMS_CONTENT_MANAGE,
                ApplicationPermission.CMS_CONTENT_PUBLISH, ApplicationPermission.MARKETING_MEDIA_MANAGE,
                ApplicationPermission.REPORT_ADMISSIONS_READ));
        values.put(AppRole.TEACHER, permissions(
                ApplicationPermission.IDENTITY_PROFILE_SELF_READ, ApplicationPermission.IDENTITY_PROFILE_SELF_UPDATE,
                ApplicationPermission.ENROLLMENT_READ, ApplicationPermission.COURSE_READ,
                ApplicationPermission.SESSION_READ, ApplicationPermission.ATTENDANCE_READ,
                ApplicationPermission.ATTENDANCE_MARK, ApplicationPermission.PROGRESS_READ,
                ApplicationPermission.LIBRARY_PUBLISHED_READ, ApplicationPermission.LIBRARY_DRAFT_CREATE,
                ApplicationPermission.LIBRARY_DRAFT_UPDATE_OWN, ApplicationPermission.ACADEMIC_MEDIA_MANAGE_OWN,
                ApplicationPermission.TEST_READ, ApplicationPermission.TEST_DRAFT_CREATE,
                ApplicationPermission.TEST_DRAFT_UPDATE_OWN, ApplicationPermission.TEST_ASSIGNMENT_MANAGE,
                ApplicationPermission.ASSESSMENT_RESULT_READ, ApplicationPermission.ASSESSMENT_GRADE));
        values.put(AppRole.STUDENT_SUPPORT, permissions(
                ApplicationPermission.IDENTITY_PROFILE_SELF_READ, ApplicationPermission.IDENTITY_PROFILE_SELF_UPDATE,
                ApplicationPermission.ENROLLMENT_READ, ApplicationPermission.COURSE_READ,
                ApplicationPermission.SESSION_READ, ApplicationPermission.ATTENDANCE_READ,
                ApplicationPermission.PROGRESS_READ, ApplicationPermission.ASSESSMENT_RESULT_READ));
        values.put(AppRole.STUDENT, permissions(
                ApplicationPermission.IDENTITY_PROFILE_SELF_READ, ApplicationPermission.IDENTITY_PROFILE_SELF_UPDATE,
                ApplicationPermission.ENROLLMENT_READ, ApplicationPermission.COURSE_READ,
                ApplicationPermission.SESSION_READ, ApplicationPermission.LIBRARY_PUBLISHED_READ,
                ApplicationPermission.ASSESSMENT_RESULT_READ, ApplicationPermission.ASSESSMENT_ATTEMPT,
                ApplicationPermission.REPORT_ACADEMIC_READ));

        // Temporary grants preserve existing manager and CMS editor access while
        // accounts/JWTs are migrated. No new invitation can create these roles.
        values.put(AppRole.MANAGER, permissions(
                ApplicationPermission.IDENTITY_PROFILE_SELF_READ, ApplicationPermission.IDENTITY_PROFILE_SELF_UPDATE,
                ApplicationPermission.IDENTITY_TEACHER_OPTIONS_READ, ApplicationPermission.ADMISSIONS_LEAD_READ,
                ApplicationPermission.ADMISSIONS_LEAD_MANAGE, ApplicationPermission.ADMISSIONS_LEAD_CONVERT,
                ApplicationPermission.STUDENT_PROFILE_READ, ApplicationPermission.STUDENT_PROFILE_CONTACT_MANAGE,
                ApplicationPermission.STUDENT_PROFILE_ACADEMIC_MANAGE, ApplicationPermission.ENROLLMENT_READ,
                ApplicationPermission.ENROLLMENT_CREATE, ApplicationPermission.ENROLLMENT_LIFECYCLE_REQUEST,
                ApplicationPermission.ENROLLMENT_LIFECYCLE_APPROVE, ApplicationPermission.ENROLLMENT_STATUS_MANAGE,
                ApplicationPermission.ENROLLMENT_EXAM_PLAN_MANAGE, ApplicationPermission.COURSE_READ,
                ApplicationPermission.COURSE_MANAGE, ApplicationPermission.COURSE_TEACHER_ASSIGN,
                ApplicationPermission.SCHEDULE_TEMPLATE_MANAGE, ApplicationPermission.SESSION_READ,
                ApplicationPermission.SESSION_MANAGE, ApplicationPermission.ATTENDANCE_READ,
                ApplicationPermission.ATTENDANCE_MARK, ApplicationPermission.ATTENDANCE_REOPEN,
                ApplicationPermission.PROGRESS_READ, ApplicationPermission.LIBRARY_PUBLISHED_READ,
                ApplicationPermission.LIBRARY_DRAFT_CREATE, ApplicationPermission.LIBRARY_DRAFT_UPDATE_OWN,
                ApplicationPermission.LIBRARY_PUBLISH, ApplicationPermission.ACADEMIC_MEDIA_MANAGE_OWN,
                ApplicationPermission.TEST_READ, ApplicationPermission.TEST_DRAFT_CREATE,
                ApplicationPermission.TEST_DRAFT_UPDATE_OWN, ApplicationPermission.TEST_PUBLISH,
                ApplicationPermission.TEST_ASSIGNMENT_MANAGE, ApplicationPermission.ASSESSMENT_RESULT_READ,
                ApplicationPermission.ASSESSMENT_GRADE));
        values.put(AppRole.CMS_EDITOR, values.get(AppRole.SOCIAL_MEDIA));
        values.put(AppRole.TEACHING_ASSISTANT, permissions(
                ApplicationPermission.IDENTITY_PROFILE_SELF_READ, ApplicationPermission.IDENTITY_PROFILE_SELF_UPDATE));
        return Map.copyOf(values);
    }

    private static Set<ApplicationPermission> permissions(ApplicationPermission... values) {
        var permissions = EnumSet.noneOf(ApplicationPermission.class);
        Collections.addAll(permissions, values);
        return Set.copyOf(permissions);
    }
}
