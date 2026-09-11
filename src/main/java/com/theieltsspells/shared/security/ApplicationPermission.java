package com.theieltsspells.shared.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Stable permission keys used by API authorization. Role grants belong in
 * {@link PermissionPolicy}; controllers must not reintroduce role lists.
 */
public enum ApplicationPermission {
    IDENTITY_PROFILE_SELF_READ("identity.profile.self.read"),
    IDENTITY_PROFILE_SELF_UPDATE("identity.profile.self.update"),
    IDENTITY_STAFF_READ("identity.staff.read"),
    IDENTITY_STAFF_MANAGE("identity.staff.manage"),
    IDENTITY_ROLE_MANAGE("identity.role.manage"),
    IDENTITY_TEACHER_OPTIONS_READ("identity.teacher_options.read"),
    AUDIT_READ("audit.read"),
    SYSTEM_SETTINGS_MANAGE("system.settings.manage"),

    ADMISSIONS_LEAD_READ("admissions.lead.read"),
    ADMISSIONS_LEAD_MANAGE("admissions.lead.manage"),
    ADMISSIONS_LEAD_CONVERT("admissions.lead.convert"),
    STUDENT_PROFILE_READ("student.profile.read"),
    STUDENT_PROFILE_CONTACT_MANAGE("student.profile.update_contact"),
    STUDENT_PROFILE_ACADEMIC_MANAGE("student.profile.update_academic"),
    ENROLLMENT_READ("enrollment.read"),
    ENROLLMENT_CREATE("enrollment.create"),
    ENROLLMENT_LIFECYCLE_REQUEST("enrollment.lifecycle.request"),
    ENROLLMENT_LIFECYCLE_APPROVE("enrollment.lifecycle.approve"),
    ENROLLMENT_STATUS_MANAGE("enrollment.status.manage"),
    ENROLLMENT_EXAM_PLAN_MANAGE("enrollment.exam_plan.manage"),

    COURSE_READ("course.read"),
    COURSE_MANAGE("course.manage"),
    COURSE_TEACHER_ASSIGN("course.teacher.assign"),
    COURSE_STUDENT_SUPPORT_ASSIGN("course.student_support.assign"),
    SCHEDULE_TEMPLATE_MANAGE("schedule.template.manage"),
    SESSION_READ("session.read"),
    SESSION_MANAGE("session.manage"),
    ATTENDANCE_READ("attendance.read"),
    ATTENDANCE_MARK("attendance.mark"),
    ATTENDANCE_REOPEN("attendance.reopen"),
    PROGRESS_READ("progress.read"),

    LIBRARY_PUBLISHED_READ("library.published.read"),
    LIBRARY_DRAFT_CREATE("library.draft.create"),
    LIBRARY_DRAFT_UPDATE_OWN("library.draft.update_own"),
    LIBRARY_PUBLISH("library.publish"),
    ACADEMIC_MEDIA_MANAGE_OWN("academic_media.manage_own"),
    TEST_READ("test.read"),
    TEST_DRAFT_CREATE("test.draft.create"),
    TEST_DRAFT_UPDATE_OWN("test.draft.update_own"),
    TEST_PUBLISH("test.publish"),
    TEST_ASSIGNMENT_MANAGE("test.assignment.manage"),
    ASSESSMENT_RESULT_READ("assessment.result.read"),
    ASSESSMENT_GRADE("assessment.grade"),
    ASSESSMENT_ATTEMPT("assessment.attempt"),

    CMS_CONTENT_READ("cms.content.read"),
    CMS_CONTENT_MANAGE("cms.content.manage"),
    CMS_CONTENT_PUBLISH("cms.content.publish"),
    MARKETING_MEDIA_MANAGE("marketing_media.manage"),
    REPORT_EXECUTIVE_READ("report.executive.read"),
    REPORT_ADMISSIONS_READ("report.admissions.read"),
    REPORT_ACADEMIC_READ("report.academic.read"),
    REPORT_OPERATIONS_READ("report.operations.read");

    private final String key;

    ApplicationPermission(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<ApplicationPermission> fromKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
