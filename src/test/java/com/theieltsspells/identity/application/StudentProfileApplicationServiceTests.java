package com.theieltsspells.identity.application;

import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.infrastructure.SupabaseAdminClient;
import com.theieltsspells.identity.infrastructure.persistence.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentProfileApplicationServiceTests {

    @Test
    void updatesAndNormalizesTheStudentProfile() {
        ProfileRepository profiles = mock(ProfileRepository.class);
        SupabaseAdminClient supabase = mock(SupabaseAdminClient.class);
        StudentProfileApplicationService service = new StudentProfileApplicationService(profiles, supabase);
        UUID studentId = UUID.randomUUID();
        Profile profile = new Profile();
        profile.setId(studentId);
        profile.setFullName("Tên cũ");
        profile.setEmail("student@example.com");
        profile.setIsActive(true);
        profile.setCreatedAt(OffsetDateTime.now());
        profile.setUpdatedAt(OffsetDateTime.now());
        when(profiles.findById(studentId)).thenReturn(Optional.of(profile));

        var response = service.update(studentId, "  Nguyễn Hoàng Phong  ", "   ");

        assertThat(response.fullName()).isEqualTo("Nguyễn Hoàng Phong");
        assertThat(response.phone()).isNull();
        ArgumentCaptor<Profile> savedProfile = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(savedProfile.capture());
        assertThat(savedProfile.getValue().getFullName()).isEqualTo("Nguyễn Hoàng Phong");
        assertThat(savedProfile.getValue().getPhone()).isNull();
        verify(supabase).upsertProfile(
                org.mockito.ArgumentMatchers.eq(studentId),
                org.mockito.ArgumentMatchers.eq("Nguyễn Hoàng Phong"),
                org.mockito.ArgumentMatchers.eq("student@example.com"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                any(OffsetDateTime.class));
    }
}
