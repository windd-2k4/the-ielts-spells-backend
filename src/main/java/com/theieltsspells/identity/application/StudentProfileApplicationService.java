package com.theieltsspells.identity.application;

import com.theieltsspells.identity.application.dto.StudentProfileResponse;
import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.infrastructure.SupabaseAdminClient;
import com.theieltsspells.identity.infrastructure.persistence.ProfileRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentProfileApplicationService {
    private final ProfileRepository profiles;
    private final SupabaseAdminClient supabase;

    @Transactional
    public StudentProfileResponse update(UUID studentId, String fullName, String phone) {
        Profile profile = activeProfile(studentId);
        String normalizedName = fullName == null ? "" : fullName.trim();
        if (normalizedName.isEmpty()) {
            throw new BusinessRuleException("Họ và tên không được để trống");
        }

        profile.setFullName(normalizedName);
        profile.setPhone(normalizeOptional(phone));
        syncAndSave(profile);
        return response(profile);
    }

    @Transactional
    public StudentProfileResponse updateAvatar(UUID studentId, String avatarPath) {
        Profile profile = activeProfile(studentId);
        profile.setAvatarPath(avatarPath);
        syncAndSave(profile);
        return response(profile);
    }

    private Profile activeProfile(UUID studentId) {
        Profile profile = profiles.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ học viên"));
        if (Boolean.FALSE.equals(profile.getIsActive())) {
            throw new BusinessRuleException("Tài khoản học viên đã bị vô hiệu hóa");
        }
        return profile;
    }

    private void syncAndSave(Profile profile) {
        OffsetDateTime now = OffsetDateTime.now();
        supabase.upsertProfile(
                profile.getId(),
                profile.getFullName(),
                profile.getEmail(),
                profile.getPhone(),
                profile.getAvatarPath(),
                now);
        profile.setUpdatedAt(now);
        profiles.save(profile);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private StudentProfileResponse response(Profile profile) {
        return new StudentProfileResponse(profile.getFullName(), profile.getPhone(), profile.getAvatarPath());
    }
}
