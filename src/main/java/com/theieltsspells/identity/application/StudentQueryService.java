package com.theieltsspells.identity.application;

import com.theieltsspells.identity.application.dto.StudentDetailResponse;
import com.theieltsspells.identity.application.dto.StudentSearchResponse;
import com.theieltsspells.identity.domain.StudentProfile;
import com.theieltsspells.identity.infrastructure.persistence.StudentProfileRepository;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentQueryService {
    private final StudentProfileRepository repository;

    public Page<StudentSearchResponse> search(String rawQuery, Pageable pageable) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        String phoneQuery = query.replaceAll("[^0-9+]", "");
        return repository.searchActiveStudents(query, phoneQuery, pageable).map(this::toResponse);
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

    private StudentSearchResponse toResponse(StudentProfile student) {
        var profile = student.getUserRef();
        return new StudentSearchResponse(
                student.getUserId(), student.getStudentCode(), profile.getFullName(), profile.getEmail(),
                profile.getPhone(), profile.getAvatarPath(), student.getCurrentBand(), student.getTargetBand());
    }
}
