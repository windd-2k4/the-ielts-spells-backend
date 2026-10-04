package com.theieltsspells.identity.application;

import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.infrastructure.SupabaseAdminClient;
import com.theieltsspells.identity.infrastructure.persistence.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentAccountApplicationService {
    private final ProfileRepository profiles;
    private final SupabaseAdminClient supabase;
    private final StudentOnboardingService onboarding;

    public Optional<UUID> findUserIdByEmail(String email) {
        return profiles.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(normalizeEmail(email))
                .map(Profile::getId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentAccount reserveOrCreate(String email, String fullName) {
        String normalizedEmail = normalizeEmail(email);
        UUID userId = supabase.reserveStudentAccount(normalizedEmail, fullName);
        var account = onboarding.onboard(userId, normalizedEmail, fullName);
        return new StudentAccount(account.userId(), account.fullName());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentAccount activateOrCreate(String email, String fullName, String password) {
        String normalizedEmail = normalizeEmail(email);
        UUID userId = supabase.activateStudentAccount(normalizedEmail, fullName, password);
        var account = onboarding.onboard(userId, normalizedEmail, fullName);
        return new StudentAccount(account.userId(), account.fullName());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
