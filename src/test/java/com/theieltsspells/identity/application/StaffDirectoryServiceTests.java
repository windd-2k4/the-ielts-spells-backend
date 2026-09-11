package com.theieltsspells.identity.application;

import com.theieltsspells.identity.domain.StaffProfile;
import com.theieltsspells.identity.domain.StaffStatus;
import com.theieltsspells.identity.infrastructure.persistence.StaffProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.TeacherProfileRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.persistence.enums.AppRole;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffDirectoryServiceTests {

    private final StaffProfileRepository staffProfiles = mock(StaffProfileRepository.class);
    private final TeacherProfileRepository teacherProfiles = mock(TeacherProfileRepository.class);
    private final StaffDirectoryService service = new StaffDirectoryService(staffProfiles, teacherProfiles);

    @Test
    void acceptsAnActiveStudentSupportAccount() {
        UUID userId = UUID.randomUUID();
        when(staffProfiles.findByAuthUserId(userId)).thenReturn(Optional.of(staff(
                userId, AppRole.STUDENT_SUPPORT, StaffStatus.ACTIVE)));

        assertThatCode(() -> service.requireActiveStudentSupport(userId)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnInactiveOrWrongRoleStudentSupportAccount() {
        UUID userId = UUID.randomUUID();
        when(staffProfiles.findByAuthUserId(userId)).thenReturn(Optional.of(staff(
                userId, AppRole.TEACHER, StaffStatus.ACTIVE)));

        assertThatThrownBy(() -> service.requireActiveStudentSupport(userId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Chỉ có thể phân công nhân sự Student Support đang hoạt động");
    }

    private static StaffProfile staff(UUID userId, AppRole role, StaffStatus status) {
        var staff = new StaffProfile();
        staff.setAuthUserId(userId);
        staff.setPrimaryRole(role);
        staff.setStatus(status);
        return staff;
    }
}
