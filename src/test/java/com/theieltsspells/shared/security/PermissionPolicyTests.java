package com.theieltsspells.shared.security;

import com.theieltsspells.academic.application.AcademicMembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionPolicyTests {

    @Mock
    private AcademicMembershipService memberships;

    private PermissionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PermissionPolicy(memberships);
    }

    @Test
    void administratorHasEveryDefinedPermission() {
        assertThat(policy.has(authentication(UUID.randomUUID(), "admin"), "test.publish")).isTrue();
        assertThat(policy.has(authentication(UUID.randomUUID(), "admin"), "identity.staff.manage")).isTrue();
    }

    @Test
    void admissionsCanConvertLeadsButCannotApproveLifecycleRequests() {
        var authentication = authentication(UUID.randomUUID(), "admissions");

        assertThat(policy.has(authentication, "admissions.lead.convert")).isTrue();
        assertThat(policy.has(authentication, "enrollment.lifecycle.approve")).isFalse();
    }

    @Test
    void socialMediaOnlyReceivesContentAndMarketingPermissions() {
        var authentication = authentication(UUID.randomUUID(), "SOCIAL_MEDIA");

        assertThat(policy.has(authentication, "cms.content.manage")).isTrue();
        assertThat(policy.has(authentication, "student.profile.read")).isFalse();
    }

    @Test
    void teacherCourseAccessRequiresAnAssignment() {
        var teacherId = UUID.randomUUID();
        var courseId = UUID.randomUUID();
        var authentication = authentication(teacherId, "teacher");
        when(memberships.isTeacherAssigned(courseId, teacherId)).thenReturn(true);

        assertThat(policy.hasForCourse(authentication, courseId, "attendance.mark")).isTrue();
    }

    @Test
    void studentCourseAccessRequiresAnActiveEnrollment() {
        var studentId = UUID.randomUUID();
        var courseId = UUID.randomUUID();
        var authentication = authentication(studentId, "student");
        when(memberships.hasActiveEnrollment(courseId, studentId)).thenReturn(false);

        assertThat(policy.hasForCourse(authentication, courseId, "course.read")).isFalse();
    }

    @Test
    void learnerCannotUseUnscopedBackOfficeEndpoints() {
        var student = authentication(UUID.randomUUID(), "student");

        assertThat(policy.hasAdministrativeScope(student, "enrollment.read")).isFalse();
        assertThat(policy.canAccessLibraryAdministration(student)).isFalse();
    }

    @Test
    void studentSupportCannotUseRawAuthoringContentEndpoints() {
        var support = authentication(UUID.randomUUID(), "student_support");

        assertThat(policy.canAccessLibraryAdministration(support)).isFalse();
        assertThat(policy.canViewTestBank(support)).isFalse();
    }

    @Test
    void studentSupportCourseAccessRequiresAnExplicitAssignment() {
        var supportId = UUID.randomUUID();
        var courseId = UUID.randomUUID();
        var support = authentication(supportId, "student_support");

        when(memberships.isStudentSupportAssigned(courseId, supportId)).thenReturn(false);
        assertThat(policy.hasForCourse(support, courseId, "progress.read")).isFalse();
        assertThat(policy.hasAdministrativeScope(support, "enrollment.read")).isFalse();

        when(memberships.isStudentSupportAssigned(courseId, supportId)).thenReturn(true);
        assertThat(policy.hasForCourse(support, courseId, "progress.read")).isTrue();
        assertThat(policy.isStudentSupportAssignedToCourse(support, courseId)).isTrue();
    }

    @Test
    void studentSupportCannotManageTestAssignmentsAcrossCourses() {
        assertThat(policy.canManageTestAssignmentsAcrossCourses(
                authentication(UUID.randomUUID(), "student_support"))).isFalse();
        assertThat(policy.canManageTestAssignmentsAcrossCourses(
                authentication(UUID.randomUUID(), "teacher"))).isFalse();
    }

    @Test
    void cmsPublishingRequiresContentPublisherPermission() {
        assertThat(policy.canChangeCmsPublication(
                authentication(UUID.randomUUID(), "social_media"), "PUBLISHED")).isTrue();
        assertThat(policy.canChangeCmsPublication(
                authentication(UUID.randomUUID(), "teacher"), "DRAFT")).isFalse();
    }

    @Test
    void unknownPermissionsAndRolesAreDenied() {
        var authentication = authentication(UUID.randomUUID(), "unknown_role");

        assertThat(policy.has(authentication, "unknown.permission")).isFalse();
        assertThat(policy.has(authentication, "course.read")).isFalse();
    }

    @Test
    void legacyManagerAccessRemainsDuringManualMigration() {
        assertThat(policy.has(authentication(UUID.randomUUID(), "manager"), "test.publish")).isTrue();
    }

    private Authentication authentication(UUID subject, String... roles) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(subject.toString()).build();
        var authorities = Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        return new JwtAuthenticationToken(jwt, authorities);
    }
}
