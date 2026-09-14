package com.theieltsspells.identity.application;

import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.infrastructure.SupabaseAdminClient;
import com.theieltsspells.identity.infrastructure.persistence.ProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.StudentProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.UserRoleRepository;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentOnboardingServiceTests {
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final SupabaseAdminClient supabase = mock(SupabaseAdminClient.class);
    private final StudentOnboardingService service = new StudentOnboardingService(
            profiles, students, roles, supabase);

    @Test
    void createsTheApplicationIdentityForANewSupabaseStudent() {
        UUID userId = UUID.fromString("0b070e99-d4ea-43f5-b3dd-f5530f2fd0d8");
        when(profiles.findById(userId)).thenReturn(Optional.empty());
        when(profiles.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.onboard(userId, "  LEARNER@example.com ", " Nguyễn Minh Anh ");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.fullName()).isEqualTo("Nguyễn Minh Anh");
        assertThat(result.studentCode()).isEqualTo("HV-0B070E99D4EA");
        verify(students).ensureProfile(userId, "HV-0B070E99D4EA");
        verify(supabase).assignRole(eq(userId), eq("STUDENT"), eq(null), any());
        verify(roles).upsertRole(eq(userId), eq("STUDENT"), eq(null), any());
    }

    @Test
    void preservesTheExistingProfileNameWhenOnboardingIsRepeated() {
        UUID userId = UUID.randomUUID();
        var profile = new Profile();
        profile.setId(userId);
        profile.setFullName("Tên hồ sơ hiện tại");
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));
        when(profiles.save(profile)).thenReturn(profile);

        var result = service.onboard(userId, "learner@example.com", "Tên mới");

        assertThat(result.fullName()).isEqualTo("Tên hồ sơ hiện tại");
        verify(students).ensureProfile(eq(userId), any());
    }

    @Test
    void rejectsAnExistingStaffIdentity() {
        UUID userId = UUID.randomUUID();
        when(roles.existsNonStudentRole(userId)).thenReturn(true);

        assertThatThrownBy(() -> service.onboard(userId, "teacher@example.com", "Teacher"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tài khoản nhân sự không thể tự đăng ký thành học viên");
        verify(profiles, never()).save(any());
        verify(supabase, never()).assignRole(any(), any(), any(), any());
    }

    @Test
    void doesNotReactivateADisabledStudentProfile() {
        UUID userId = UUID.randomUUID();
        var profile = new Profile();
        profile.setId(userId);
        profile.setFullName("Học viên đã khóa");
        profile.setIsActive(false);
        when(profiles.findById(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.onboard(userId, "learner@example.com", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Tài khoản học viên đã bị vô hiệu hóa");
        verify(supabase, never()).upsertProfile(any(), any(), any(), any(), any(), any());
    }

}
