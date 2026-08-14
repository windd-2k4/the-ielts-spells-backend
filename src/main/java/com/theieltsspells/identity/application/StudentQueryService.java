package com.theieltsspells.identity.application;

import com.theieltsspells.identity.application.dto.StudentDetailResponse;
import com.theieltsspells.identity.application.dto.StudentLifecycleStatus;
import com.theieltsspells.identity.application.dto.StudentSearchResponse;
import com.theieltsspells.identity.application.dto.UpdateStudentRequest;
import com.theieltsspells.identity.domain.StudentProfile;
import com.theieltsspells.identity.infrastructure.persistence.StudentProfileRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentQueryService {
    private final StudentProfileRepository repository;

    public Page<StudentSearchResponse> search(String rawQuery, Boolean active, StudentLifecycleStatus lifecycle, Pageable pageable) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        String phoneQuery = query.replaceAll("[^0-9+]", "");
        String lifecycleValue = lifecycle == null ? null : lifecycle.name();
        return repository.searchDirectory(query, phoneQuery, active, lifecycleValue, pageable).map(row ->
                new StudentSearchResponse(
                        row.getId(), row.getStudentCode(), row.getFullName(), row.getEmail(), row.getPhone(),
                        row.getAvatarPath(), row.getCurrentBand(), row.getTargetBand(), Boolean.TRUE.equals(row.getActive()),
                        StudentLifecycleStatus.valueOf(row.getLifecycleStatus()), row.getCurrentCourseId(),
                        row.getCurrentCourseCode(), row.getCurrentCourseName(),
                        row.getEnrollmentCount() == null ? 0 : row.getEnrollmentCount(), row.getJoinedAt()));
    }

    public StudentDetailResponse get(UUID id) {
        StudentProfile student = repository.findByIdWithProfile(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học viên"));
        var profile = student.getUserRef();
        return new StudentDetailResponse(
                student.getUserId(), student.getStudentCode(), profile.getFullName(), profile.getEmail(),
                profile.getPhone(), profile.getAvatarPath(), student.getCurrentBand(), student.getTargetBand(),
                student.getDateOfBirth(), student.getAddress(), student.getEmergencyContact(), student.getJoinedAt(),
                student.getNotes(), Boolean.TRUE.equals(profile.getIsActive()), profile.getCreatedAt(), profile.getUpdatedAt());
    }

    @Transactional
    public StudentDetailResponse update(UUID id, UpdateStudentRequest request) {
        StudentProfile student = repository.findByIdWithProfile(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học viên"));
        var profile = student.getUserRef();
        profile.setFullName(request.fullName().trim());
        profile.setEmail(blankToNull(request.email()));
        profile.setPhone(blankToNull(request.phone()));
        if (request.targetBand() != null && student.getCurrentBand() != null
                && request.targetBand().compareTo(student.getCurrentBand()) < 0) {
            throw new BusinessRuleException("Band mục tiêu không được thấp hơn Band hiện tại");
        }
        student.setTargetBand(request.targetBand());
        student.setDateOfBirth(request.dateOfBirth());
        student.setAddress(blankToNull(request.address()));
        student.setEmergencyContact(request.emergencyContact() == null ? Map.of() : request.emergencyContact());
        student.setNotes(blankToNull(request.notes()));
        repository.save(student);
        return get(id);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

}
