package com.theieltsspells.identity.presentation;

import com.theieltsspells.identity.application.StaffInvitationApplicationService;
import com.theieltsspells.identity.application.dto.*;
import com.theieltsspells.identity.domain.InvitationStatus;
import com.theieltsspells.shared.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class StaffInvitationController {
    private final StaffInvitationApplicationService service;

    @PostMapping("/api/v1/admin/staff/invitations")
    @PreAuthorize("hasAuthority('admin')")
    public InvitationResponse create(@Valid @RequestBody CreateStaffInvitationRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.createAndInvite(request, UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("email"), displayName(jwt));
    }

    @GetMapping("/api/v1/admin/staff")
    @PreAuthorize("hasAuthority('admin')")
    public PageResponse<StaffResponse> listStaff(Pageable pageable) {
        return PageResponse.from(service.listStaff(pageable));
    }

    @GetMapping("/api/v1/admin/staff/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public StaffResponse getStaff(@PathVariable UUID id) {
        return service.getStaff(id);
    }

    @PatchMapping("/api/v1/admin/staff/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public StaffResponse updateStaff(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateStaffRequest request) {
        return service.updateStaff(id, request);
    }

    @PatchMapping("/api/v1/admin/staff/{id}/role")
    @PreAuthorize("hasAuthority('admin')")
    public StaffResponse changeRole(@PathVariable UUID id,
                                    @Valid @RequestBody ChangeStaffRoleRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return service.changeRole(id, request.role(), UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/api/v1/admin/staff/{id}/status")
    @PreAuthorize("hasAuthority('admin')")
    public StaffResponse changeStatus(@PathVariable UUID id,
                                      @Valid @RequestBody ChangeStaffStatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    @GetMapping("/api/v1/admin/staff/invitations")
    @PreAuthorize("hasAuthority('admin')")
    public PageResponse<InvitationResponse> listInvitations(@RequestParam(required = false) InvitationStatus status,
                                                            Pageable pageable) {
        return PageResponse.from(service.listInvitations(status, pageable));
    }

    @PostMapping("/api/v1/admin/staff/invitations/{id}/revoke")
    @PreAuthorize("hasAuthority('admin')")
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
