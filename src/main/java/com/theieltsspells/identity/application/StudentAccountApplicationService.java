package com.theieltsspells.identity.application;

import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.domain.UserRole;
import com.theieltsspells.identity.infrastructure.persistence.ProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.StudentProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.UserRoleRepository;
import com.theieltsspells.shared.persistence.enums.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentAccountApplicationService {
    private final ProfileRepository profiles;
    private final StudentProfileRepository students;
    private final UserRoleRepository roles;
    private final JdbcTemplate jdbc;

    public Optional<UUID> findUserIdByEmail(String email) {
        return profiles.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(normalizeEmail(email))
                .map(Profile::getId);
    }

    @Transactional
    public StudentAccount activateOrCreate(String email, String fullName) {
        String normalizedEmail = normalizeEmail(email);
        OffsetDateTime now = OffsetDateTime.now();
        Profile profile = profiles.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(normalizedEmail).orElse(null);
        boolean newAccount = profile == null;

        if (newAccount) {
            UUID userId = UUID.randomUUID();
            jdbc.update("insert into auth.users (id) values (?) on conflict (id) do nothing", userId);

            profile = new Profile();
            profile.setId(userId);
            profile.setFullName(fullName);
            profile.setEmail(normalizedEmail);
            profile.setCreatedAt(now);
        }

        profile.setIsActive(true);
        profile.setUpdatedAt(now);
        Profile saved = profiles.save(profile);

        if (newAccount) {
            UserRole studentRole = new UserRole();
            studentRole.setUserId(saved.getId());
            studentRole.setRole(AppRole.STUDENT);
            studentRole.setAssignedAt(now);
            roles.save(studentRole);

            String studentCode = "HV" + System.currentTimeMillis() % 1_000_000;
            students.ensureProfile(saved.getId(), studentCode);
        }

        return new StudentAccount(saved.getId(), saved.getFullName());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
