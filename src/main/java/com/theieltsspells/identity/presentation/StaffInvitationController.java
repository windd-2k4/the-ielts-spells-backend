package com.theieltsspells.identity.presentation;

import com.theieltsspells.identity.application.StaffInvitationApplicationService;
import com.theieltsspells.identity.application.StaffAvatarApplicationService;
import com.theieltsspells.identity.application.dto.*;
import com.theieltsspells.identity.domain.InvitationStatus;
import com.theieltsspells.shared.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class StaffInvitationController {
    private final StaffInvitationApplicationService service;
    private final StaffAvatarApplicationService avatarService;

    @PostMapping("/api/v1/admin/staff/invitations")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.staff.manage')")
    public InvitationResponse create(@Valid @RequestBody CreateStaffInvitationRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.createAndInvite(request, UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("email"), displayName(jwt));
    }

    @PostMapping(value = "/api/v1/admin/staff/avatars", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.staff.manage')")
    public StaffAvatarUploadResponse uploadAvatar(@RequestParam MultipartFile file) {
        String filename = avatarService.upload(file);
        String avatarPath = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/public/staff-avatars/{filename}")
                .buildAndExpand(filename)
                .toUriString();
        return new StaffAvatarUploadResponse(avatarPath, filename);
    }

    @GetMapping("/api/v1/public/staff-avatars/{filename}")
    public ResponseEntity<InputStreamResource> avatar(@PathVariable String filename) {
        var content = avatarService.open(filename);
        return ResponseEntity.ok().contentType(content.mediaType())
                .body(new InputStreamResource(content.inputStream()));
    }

    @GetMapping("/api/v1/admin/staff")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.staff.read')")
    public PageResponse<StaffResponse> listStaff(Pageable pageable) {
        return PageResponse.from(service.listStaff(pageable));
    }

    @GetMapping("/api/v1/admin/staff/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.staff.read')")
    public StaffResponse getStaff(@PathVariable UUID id) {
        return service.getStaff(id);
    }

    @GetMapping("/api/v1/admin/teacher-options")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.teacher_options.read')")
    public List<TeacherOptionResponse> listTeacherOptions() {
        return service.listStaff(PageRequest.of(0, 500)).stream()
                .filter(staff -> staff.authUserId() != null)
                .filter(staff -> staff.status() == com.theieltsspells.identity.domain.StaffStatus.ACTIVE)
                .filter(staff -> staff.role() == com.theieltsspells.shared.persistence.enums.AppRole.TEACHER
                        || staff.role() == com.theieltsspells.shared.persistence.enums.AppRole.TEACHING_ASSISTANT)
                .map(staff -> new TeacherOptionResponse(
                        staff.authUserId(), staff.fullName(), staff.email(), staff.role()))
                .toList();
    }

    @PatchMapping("/api/v1/admin/staff/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.staff.manage')")
    public StaffResponse updateStaff(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateStaffRequest request) {
        return service.updateStaff(id, request);
    }

    @PatchMapping("/api/v1/admin/staff/{id}/role")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.role.manage')")
    public StaffResponse changeRole(@PathVariable UUID id,
                                    @Valid @RequestBody ChangeStaffRoleRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return service.changeRole(id, request.role(), UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/api/v1/admin/staff/{id}/status")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.staff.manage')")
    public StaffResponse changeStatus(@PathVariable UUID id,
                                      @Valid @RequestBody ChangeStaffStatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    @GetMapping("/api/v1/admin/staff/invitations")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.staff.read')")
    public PageResponse<InvitationResponse> listInvitations(@RequestParam(required = false) InvitationStatus status,
                                                            Pageable pageable) {
        return PageResponse.from(service.listInvitations(status, pageable));
    }

    @PostMapping("/api/v1/admin/staff/invitations/{id}/revoke")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.staff.manage')")
    public InvitationResponse revoke(@PathVariable UUID id) {
        return service.revoke(id);
    }

    @GetMapping("/api/v1/auth/invitations/current")
    public InvitationResponse current(@AuthenticationPrincipal Jwt jwt) {
        return service.current(jwt.getClaimAsString("email"));
    }

    @PostMapping("/api/v1/auth/invitations/activate")
    public StaffResponse activate(@Valid @RequestBody ActivateStaffRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.activate(jwt.getClaimAsString("email"), UUID.fromString(jwt.getSubject()), request);
    }

    private String displayName(Jwt jwt) {
        Object metadata = jwt.getClaim("user_metadata");
        if (metadata instanceof Map<?, ?> values) {
            Object name = values.get("full_name");
            if (name == null) name = values.get("name");
            if (name != null && !name.toString().isBlank()) return name.toString();
        }
        String email = jwt.getClaimAsString("email");
        return email == null ? "Administrator" : email;
    }
}
