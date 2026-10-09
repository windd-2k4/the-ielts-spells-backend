package com.theieltsspells.identity.application;

import com.theieltsspells.identity.application.dto.ActivateStaffRequest;
import com.theieltsspells.identity.application.dto.CreateStaffInvitationRequest;
import com.theieltsspells.identity.domain.InvitationStatus;
import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.domain.StaffInvitation;
import com.theieltsspells.identity.domain.StaffProfile;
import com.theieltsspells.identity.domain.StaffStatus;
import com.theieltsspells.identity.infrastructure.SupabaseAdminClient;
import com.theieltsspells.identity.infrastructure.StaffInvitationEmailService;
import com.theieltsspells.identity.infrastructure.persistence.ProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.StaffInvitationRepository;
import com.theieltsspells.identity.infrastructure.persistence.StaffProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.TeacherProfileRepository;
import com.theieltsspells.identity.infrastructure.persistence.UserRoleRepository;
import com.theieltsspells.shared.persistence.enums.AppRole;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffInvitationApplicationServiceTests {

    @Test
    void allowsInvitingTheBusinessOwnerAsAdmin() {
        StaffProfileRepository staffProfiles = mock(StaffProfileRepository.class);
        StaffInvitationRepository invitations = mock(StaffInvitationRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);
        TeacherProfileRepository teacherProfiles = mock(TeacherProfileRepository.class);
        SupabaseAdminClient supabase = mock(SupabaseAdminClient.class);
        StaffInvitationEmailService invitationEmail = mock(StaffInvitationEmailService.class);
        StaffInvitationApplicationService service = new StaffInvitationApplicationService(
                staffProfiles, invitations, profiles, roles, teacherProfiles, supabase, invitationEmail);

        UUID adminId = UUID.fromString("4f789b43-f96b-4702-80e5-3c02a376c15e");
        UUID ownerUserId = UUID.fromString("8d58c7d1-8e8c-446e-9080-6c6964a5d57f");
        UUID staffId = UUID.fromString("f34f5cf7-0c95-4980-b34b-e173d25f01f8");
        when(profiles.existsById(adminId)).thenReturn(true);
        when(supabase.inviteStaff("owner@example.com", "Business Owner"))
                .thenReturn(new SupabaseAdminClient.StaffInvite(ownerUserId, "https://example.com/invite"));
        when(staffProfiles.save(any(StaffProfile.class))).thenAnswer(invocation -> {
            StaffProfile staff = invocation.getArgument(0);
            staff.setId(staffId);
            return staff;
        });
        when(invitations.save(any(StaffInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createAndInvite(new CreateStaffInvitationRequest(
                "Business Owner", "owner@example.com", null, null, null, null,
                null, null, AppRole.ADMIN, null, null, null, null),
                adminId, "admin@example.com", "Administrator");

        assertThat(result.intendedRole()).isEqualTo(AppRole.ADMIN);
        assertThat(result.staffProfileId()).isEqualTo(staffId);
        verify(supabase).inviteStaff("owner@example.com", "Business Owner");
        verify(invitationEmail).send("owner@example.com", "Business Owner", AppRole.ADMIN,
                "https://example.com/invite");
    }

    @Test
    void upsertsSupabaseProfileBeforeLoadingTheApplicationProfile() {
        StaffProfileRepository staffProfiles = mock(StaffProfileRepository.class);
        StaffInvitationRepository invitations = mock(StaffInvitationRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);
        TeacherProfileRepository teacherProfiles = mock(TeacherProfileRepository.class);
        SupabaseAdminClient supabase = mock(SupabaseAdminClient.class);
        StaffInvitationEmailService invitationEmail = mock(StaffInvitationEmailService.class);
        StaffInvitationApplicationService service = new StaffInvitationApplicationService(
                staffProfiles, invitations, profiles, roles, teacherProfiles, supabase, invitationEmail);

        UUID authUserId = UUID.fromString("7f14e018-ff88-4d2c-99ad-094bc593f54e");
        UUID staffId = UUID.fromString("9f8f8376-34df-4dfe-a12b-c43031b3c675");
        UUID adminId = UUID.fromString("4f789b43-f96b-4702-80e5-3c02a376c15e");
        OffsetDateTime now = OffsetDateTime.now();

        StaffInvitation invitation = new StaffInvitation();
        invitation.setId(UUID.fromString("1425a1c2-3054-4e03-b833-97d2353da96c"));
        invitation.setStaffProfileId(staffId);
        invitation.setEmail("teacher@example.com");
        invitation.setIntendedRole(AppRole.TEACHER);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setInvitedBy(adminId);
        invitation.setInvitedAt(now.minusHours(1));
        invitation.setExpiresAt(now.plusHours(24));
        invitation.setSupabaseUserId(authUserId);

        StaffProfile staff = new StaffProfile();
        staff.setId(staffId);
        staff.setAuthUserId(authUserId);
        staff.setFullName("Teacher One");
        staff.setEmail("teacher@example.com");
        staff.setPrimaryRole(AppRole.TEACHER);
        staff.setStatus(StaffStatus.INVITED);
        staff.setCreatedAt(now.minusHours(1));
        staff.setUpdatedAt(now.minusHours(1));

        Profile profile = new Profile();
        profile.setId(authUserId);
        profile.setFullName("Teacher One");
        profile.setEmail("teacher@example.com");
        profile.setIsActive(true);
        profile.setCreatedAt(now.minusHours(1));
        profile.setUpdatedAt(now.minusHours(1));

        when(invitations.findFirstByEmailIgnoreCaseAndStatusOrderByInvitedAtDesc(
                "teacher@example.com", InvitationStatus.PENDING)).thenReturn(Optional.of(invitation));
        when(staffProfiles.findById(staffId)).thenReturn(Optional.of(staff));
        when(profiles.findById(authUserId)).thenReturn(Optional.of(profile));

        var result = service.activate("teacher@example.com", authUserId,
                new ActivateStaffRequest("Teacher Updated"));

        InOrder synchronizationOrder = inOrder(supabase, profiles);
        synchronizationOrder.verify(supabase).upsertProfile(eq(authUserId), eq("Teacher Updated"),
                eq("teacher@example.com"), isNull(), isNull(), any(OffsetDateTime.class));
        synchronizationOrder.verify(profiles).findById(authUserId);
        verify(supabase).assignRole(eq(authUserId), eq("TEACHER"), eq(adminId), any(OffsetDateTime.class));
        verify(roles).upsertRole(eq(authUserId), eq("TEACHER"), eq(adminId), any(OffsetDateTime.class));
        verify(teacherProfiles).ensureProfile(authUserId,
                "GV-" + authUserId.toString().replace("-", ""));
        assertThat(result.status()).isEqualTo(StaffStatus.ACTIVE);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    void revokingInvitationRemovesDraftStaffAndSupabaseUser() {
        StaffProfileRepository staffProfiles = mock(StaffProfileRepository.class);
        StaffInvitationRepository invitations = mock(StaffInvitationRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);
        TeacherProfileRepository teacherProfiles = mock(TeacherProfileRepository.class);
        SupabaseAdminClient supabase = mock(SupabaseAdminClient.class);
        StaffInvitationEmailService invitationEmail = mock(StaffInvitationEmailService.class);
        StaffInvitationApplicationService service = new StaffInvitationApplicationService(
                staffProfiles, invitations, profiles, roles, teacherProfiles, supabase, invitationEmail);

        UUID invitationId = UUID.fromString("1425a1c2-3054-4e03-b833-97d2353da96c");
        UUID staffId = UUID.fromString("9f8f8376-34df-4dfe-a12b-c43031b3c675");
        UUID userId = UUID.fromString("7f14e018-ff88-4d2c-99ad-094bc593f54e");
        OffsetDateTime now = OffsetDateTime.now();

        StaffInvitation invitation = new StaffInvitation();
        invitation.setId(invitationId);
        invitation.setStaffProfileId(staffId);
        invitation.setEmail("teacher@example.com");
        invitation.setIntendedRole(AppRole.TEACHER);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setInvitedAt(now);
        invitation.setExpiresAt(now.plusDays(2));
        invitation.setSupabaseUserId(userId);

        StaffProfile staff = new StaffProfile();
        staff.setId(staffId);
        staff.setFullName("Teacher One");
        staff.setEmail("teacher@example.com");
        staff.setPrimaryRole(AppRole.TEACHER);
        staff.setStatus(StaffStatus.INVITED);

        when(invitations.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(staffProfiles.findById(staffId)).thenReturn(Optional.of(staff));

        var result = service.revoke(invitationId);

        verify(supabase).deleteUser(userId);
        verify(staffProfiles).delete(staff);
        assertThat(result.status()).isEqualTo(InvitationStatus.REVOKED);
    }
}
