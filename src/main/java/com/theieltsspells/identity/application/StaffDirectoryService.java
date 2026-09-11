package com.theieltsspells.identity.application;

import com.theieltsspells.identity.domain.StaffStatus;
import com.theieltsspells.identity.infrastructure.persistence.StaffProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.TeacherProfileRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.persistence.enums.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Public identity-module API for validating staff eligibility in other modules. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffDirectoryService {

    private final StaffProfileRepository staffProfiles;
    private final TeacherProfileRepository teacherProfiles;

    public void requireActiveTeacher(UUID authUserId) {
        var staff = staffProfiles.findByAuthUserId(authUserId)
                .orElseThrow(() -> new BusinessRuleException("Giáo viên không có hồ sơ nhân sự hoạt động"));
        if (staff.getStatus() != StaffStatus.ACTIVE || staff.getPrimaryRole() != AppRole.TEACHER
                || !teacherProfiles.existsById(authUserId)) {
            throw new BusinessRuleException("Chỉ có thể phân công giáo viên đang hoạt động");
        }
    }

    public void requireActiveStudentSupport(UUID authUserId) {
        var staff = staffProfiles.findByAuthUserId(authUserId)
                .orElseThrow(() -> new BusinessRuleException("Nhân sự hỗ trợ không có hồ sơ hoạt động"));
        if (staff.getStatus() != StaffStatus.ACTIVE || staff.getPrimaryRole() != AppRole.STUDENT_SUPPORT) {
            throw new BusinessRuleException("Chỉ có thể phân công nhân sự Student Support đang hoạt động");
        }
    }
}
