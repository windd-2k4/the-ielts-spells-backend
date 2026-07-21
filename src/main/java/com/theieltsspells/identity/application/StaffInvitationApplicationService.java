package com.theieltsspells.identity.application;

import com.theieltsspells.identity.application.dto.*;
import com.theieltsspells.identity.domain.*;
import com.theieltsspells.identity.infrastructure.SupabaseAdminClient;
import com.theieltsspells.identity.infrastructure.persistence.*;
import com.theieltsspells.shared.application.*;
import com.theieltsspells.shared.persistence.enums.AppRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StaffInvitationApplicationService {
    private static final Set<AppRole> STAFF_ROLES = Set.of(
            AppRole.MANAGER, AppRole.TEACHER, AppRole.TEACHING_ASSISTANT,
            AppRole.ADMISSIONS, AppRole.CMS_EDITOR);

    private final StaffProfileRepository staffProfiles;
    private final StaffInvitationRepository invitations;
    private final ProfileRepository profiles;
    private final UserRoleRepository roles;
    private final SupabaseAdminClient supabase;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public InvitationResponse createAndInvite(CreateStaffInvitationRequest input, UUID adminId,
                                               String adminEmail, String adminName) {
        if (!STAFF_ROLES.contains(input.role()))
            throw new BusinessRuleException("Vai trò nhân sự không hợp lệ");
        ensureReviewer(adminId, adminEmail, adminName);
        String email = input.email().trim().toLowerCase();
        if (staffProfiles.existsByEmailIgnoreCase(email))
            throw new ConflictException("Email đã có hồ sơ nhân sự");
        if (invitations.existsByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING))
            throw new ConflictException("Email đã có lời mời đang chờ");

        OffsetDateTime now = OffsetDateTime.now();
        UUID supabaseUserId = supabase.inviteStaff(email, input.fullName().trim());

        StaffProfile staff = new StaffProfile();
        staff.setAuthUserId(supabaseUserId);
        staff.setFullName(input.fullName().trim());
        staff.setEmail(email);
        staff.setPhone(input.phone());
        staff.setJobTitle(input.jobTitle());
        staff.setDepartment(input.department());
        staff.setEmploymentType(input.employmentType());
        staff.setStartDate(input.startDate());
        staff.setPrimaryRole(input.role());
        staff.setCvPath(input.cvPath());
        staff.setPortfolioUrl(input.portfolioUrl());
        staff.setProfessionalSummary(input.professionalSummary());
        staff.setInternalNotes(input.internalNotes());
        staff.setStatus(StaffStatus.INVITED);
        staff.setCreatedBy(adminId);
        staff.setCreatedAt(now);
        staff.setUpdatedAt(now);
        staff = staffProfiles.save(staff);

        StaffInvitation invitation = new StaffInvitation();
        invitation.setStaffProfileId(staff.getId());
        invitation.setEmail(email);
        invitation.setIntendedRole(input.role());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setInvitedBy(adminId);
        invitation.setInvitedAt(now);
        invitation.setExpiresAt(now.plusHours(48));
        invitation.setSupabaseUserId(supabaseUserId);
        invitation.setCreatedAt(now);
        invitation.setUpdatedAt(now);
        invitation = invitations.save(invitation);
        return map(invitation, staff);
    }

    @Transactional(readOnly = true)
    public Page<StaffResponse> listStaff(Pageable pageable) {
        return staffProfiles.findAll(pageable).map(this::map);
    }

    @Transactional(readOnly = true)
    public StaffResponse getStaff(UUID id) {
        return map(findStaff(id));
    }

    @Transactional
    public StaffResponse updateStaff(UUID id, UpdateStaffRequest input) {
        StaffProfile staff = findStaff(id);
        if (input.fullName() != null) staff.setFullName(input.fullName().trim());
        if (input.phone() != null) staff.setPhone(clean(input.phone()));
        if (input.jobTitle() != null) staff.setJobTitle(clean(input.jobTitle()));
        if (input.department() != null) staff.setDepartment(clean(input.department()));
        if (input.employmentType() != null) staff.setEmploymentType(clean(input.employmentType()));
        if (input.startDate() != null) staff.setStartDate(input.startDate());
        if (input.cvPath() != null) staff.setCvPath(clean(input.cvPath()));
        if (input.portfolioUrl() != null) staff.setPortfolioUrl(clean(input.portfolioUrl()));
        if (input.professionalSummary() != null) staff.setProfessionalSummary(clean(input.professionalSummary()));
        if (input.internalNotes() != null) staff.setInternalNotes(clean(input.internalNotes()));
        staff.setUpdatedAt(OffsetDateTime.now());
        return map(staff);
    }

    @Transactional
    public StaffResponse changeRole(UUID id, AppRole nextRole, UUID adminId) {
        if (!STAFF_ROLES.contains(nextRole))
            throw new BusinessRuleException("Vai trò nhân sự không hợp lệ");
        StaffProfile staff = findStaff(id);
        AppRole previousRole = staff.getPrimaryRole();
        if (previousRole == nextRole) return map(staff);
        if (staff.getAuthUserId() != null && staff.getStatus() == StaffStatus.ACTIVE) {
            supabase.replaceRole(staff.getAuthUserId(), previousRole.getDatabaseValue(), nextRole.getDatabaseValue());
            roles.deleteRole(staff.getAuthUserId(), previousRole.getDatabaseValue());
            roles.upsertRole(staff.getAuthUserId(), nextRole.getDatabaseValue(), adminId, OffsetDateTime.now());
        }
        staff.setPrimaryRole(nextRole);
        invitations.findFirstByStaffProfileIdAndStatusOrderByInvitedAtDesc(id, InvitationStatus.PENDING)
                .ifPresent(invitation -> invitation.setIntendedRole(nextRole));
        staff.setUpdatedAt(OffsetDateTime.now());
        return map(staff);
    }

    @Transactional
    public StaffResponse changeStatus(UUID id, StaffStatus nextStatus) {
        if (nextStatus != StaffStatus.ACTIVE && nextStatus != StaffStatus.SUSPENDED
                && nextStatus != StaffStatus.OFFBOARDED)
            throw new BusinessRuleException("Chỉ có thể kích hoạt, tạm khóa hoặc cho nghỉ việc");
        StaffProfile staff = findStaff(id);
        if (staff.getAuthUserId() == null)
            throw new BusinessRuleException("Nhân sự chưa hoàn tất kích hoạt tài khoản");
        if (staff.getStatus() == StaffStatus.OFFBOARDED && nextStatus != StaffStatus.OFFBOARDED)
            throw new ConflictException("Tài khoản đã nghỉ việc không thể tự kích hoạt lại");
        supabase.setUserSuspended(staff.getAuthUserId(), nextStatus != StaffStatus.ACTIVE);
        staff.setStatus(nextStatus);
        staff.setUpdatedAt(OffsetDateTime.now());
        return map(staff);
    }

    @Transactional
    public Page<InvitationResponse> listInvitations(InvitationStatus status, Pageable pageable) {
        Page<StaffInvitation> page = status == null ? invitations.findAll(pageable)
                : invitations.findAllByStatus(status, pageable);
        return page.map(invitation -> map(invitation, staffProfiles.findById(invitation.getStaffProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ nhân sự"))));
    }

    @Transactional(readOnly = true)
    public InvitationResponse current(String email) {
        StaffInvitation invitation = pendingFor(email);
        return map(invitation, staffProfiles.findById(invitation.getStaffProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ nhân sự")));
    }

    @Transactional
    public StaffResponse activate(String email, UUID authUserId, ActivateStaffRequest input) {
        StaffInvitation invitation = pendingFor(email);
        if (invitation.getSupabaseUserId() != null && !invitation.getSupabaseUserId().equals(authUserId))
            throw new BusinessRuleException("Tài khoản xác thực không khớp với lời mời");
        StaffProfile staff = staffProfiles.findById(invitation.getStaffProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ nhân sự"));
        OffsetDateTime now = OffsetDateTime.now();
        if (input.fullName() != null && !input.fullName().isBlank()) staff.setFullName(input.fullName().trim());
        if (input.phone() != null) staff.setPhone(input.phone());
        if (input.avatarPath() != null) staff.setAvatarPath(input.avatarPath());
        if (input.professionalSummary() != null) staff.setProfessionalSummary(input.professionalSummary());
        staff.setAuthUserId(authUserId);
        staff.setStatus(StaffStatus.ACTIVE);
        staff.setActivatedAt(now);
        staff.setUpdatedAt(now);

        Profile profile = profiles.findById(authUserId).orElseGet(Profile::new);
        boolean newProfile = profile.getId() == null;
        if (newProfile) profile.setId(authUserId);
        profile.setFullName(staff.getFullName());
        profile.setEmail(staff.getEmail());
        profile.setPhone(staff.getPhone());
        profile.setAvatarPath(staff.getAvatarPath());
        profile.setIsActive(true);
        if (profile.getCreatedAt() == null) profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        if (newProfile) entityManager.persist(profile);

        supabase.assignRole(authUserId, staff.getPrimaryRole().getDatabaseValue(), invitation.getInvitedBy(), now);
        roles.upsertRole(authUserId, staff.getPrimaryRole().getDatabaseValue(), invitation.getInvitedBy(), now);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(now);
        invitation.setUpdatedAt(now);
        return map(staff);
    }

    @Transactional
    public InvitationResponse revoke(UUID id) {
        StaffInvitation invitation = invitations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lời mời"));
        if (invitation.getStatus() != InvitationStatus.PENDING)
            throw new ConflictException("Chỉ có thể thu hồi lời mời đang chờ");
        OffsetDateTime now = OffsetDateTime.now();
        invitation.setStatus(InvitationStatus.REVOKED);
        invitation.setRevokedAt(now);
        invitation.setUpdatedAt(now);
        StaffProfile staff = staffProfiles.findById(invitation.getStaffProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ nhân sự"));
        staff.setStatus(StaffStatus.DRAFT);
        staff.setUpdatedAt(now);
        return map(invitation, staff);
    }

    private StaffInvitation pendingFor(String email) {
        StaffInvitation invitation = invitations
                .findFirstByEmailIgnoreCaseAndStatusOrderByInvitedAtDesc(email, InvitationStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("Không có lời mời đang chờ cho email này"));
        if (invitation.getExpiresAt().isBefore(OffsetDateTime.now()))
            throw new BusinessRuleException("Lời mời đã hết hạn. Vui lòng liên hệ quản trị viên");
        return invitation;
    }

    private void ensureReviewer(UUID id, String email, String fullName) {
        if (profiles.existsById(id)) return;
        OffsetDateTime now = OffsetDateTime.now();
        Profile profile = new Profile();
        profile.setId(id);
        profile.setEmail(email);
        profile.setFullName(fullName == null || fullName.isBlank() ? "Administrator" : fullName);
        profile.setIsActive(true);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        entityManager.persist(profile);
    }

    private StaffResponse map(StaffProfile value) {
        return new StaffResponse(value.getId(), value.getAuthUserId(), value.getFullName(), value.getEmail(),
                value.getPhone(), value.getAvatarPath(), value.getJobTitle(), value.getDepartment(),
                value.getEmploymentType(), value.getStartDate(), value.getPrimaryRole(), value.getCvPath(),
                value.getPortfolioUrl(), value.getProfessionalSummary(), value.getInternalNotes(),
                value.getStatus(), value.getActivatedAt(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private StaffProfile findStaff(UUID id) {
        return staffProfiles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ nhân sự"));
    }

    private String clean(String value) {
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private InvitationResponse map(StaffInvitation value, StaffProfile staff) {
        return new InvitationResponse(value.getId(), value.getStaffProfileId(), value.getEmail(), staff.getFullName(),
                value.getIntendedRole(), value.getStatus(), value.getInvitedAt(), value.getExpiresAt(),
                value.getAcceptedAt());
    }
}
