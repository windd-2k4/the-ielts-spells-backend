package com.theieltsspells.identity.application;

import com.theieltsspells.identity.application.dto.StudentOnboardingResponse;
import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.infrastructure.SupabaseAdminClient;
import com.theieltsspells.identity.infrastructure.persistence.ProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.StudentProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.UserRoleRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.persistence.enums.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentOnboardingService {
    private final ProfileRepository profiles;
    private final StudentProfileRepository students;
    private final UserRoleRepository roles;
    private final SupabaseAdminClient supabase;

    @Transactional
    public StudentOnboardingResponse onboard(UUID userId, String email, String requestedFullName) {
        boolean hasStaffRole = roles.findAllByUserId(userId).stream()
                .anyMatch(role -> role.getRole() != AppRole.STUDENT);
        if (hasStaffRole) {
            throw new ConflictException("Tài khoản nhân sự không thể tự đăng ký thành học viên");
        }

        String normalizedEmail = normalizeEmail(email);
        Profile existing = profiles.findById(userId).orElse(null);
        if (existing != null && Boolean.FALSE.equals(existing.getIsActive())) {
            throw new BusinessRuleException("Tài khoản học viên đã bị vô hiệu hóa");
        }
        String fullName = resolveFullName(existing, requestedFullName, normalizedEmail);
        OffsetDateTime now = OffsetDateTime.now();

        // Keep the identity data required by the Supabase access-token hook in
        // sync even when the application database is hosted separately.
        supabase.upsertProfile(
                userId,
                fullName,
                normalizedEmail,
                existing == null ? null : existing.getPhone(),
                existing == null ? null : existing.getAvatarPath(),
                now);

        Profile profile = existing == null ? new Profile() : existing;
        profile.setId(userId);
        profile.setFullName(fullName);
        profile.setEmail(normalizedEmail);
        profile.setIsActive(true);
        if (profile.getCreatedAt() == null) profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profiles.save(profile);

        String studentCode = studentCode(userId);
        students.ensureProfile(userId, studentCode);
        supabase.assignRole(userId, AppRole.STUDENT.name(), null, now);
        roles.upsertRole(userId, AppRole.STUDENT.name(), null, now);

        return new StudentOnboardingResponse(userId, fullName, studentCode);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException("Tài khoản Supabase chưa có email hợp lệ");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveFullName(Profile existing, String requestedFullName, String email) {
        if (existing != null && existing.getFullName() != null && !existing.getFullName().isBlank()) {
            return existing.getFullName();
        }
        if (requestedFullName != null && !requestedFullName.isBlank()) {
            return requestedFullName.trim();
        }
        int separator = email.indexOf('@');
        return separator > 0 ? email.substring(0, separator) : "Học viên";
    }

    private String studentCode(UUID userId) {
        return "HV-" + userId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }
}
