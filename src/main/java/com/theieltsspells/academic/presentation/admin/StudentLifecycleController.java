package com.theieltsspells.academic.presentation.admin;

import com.theieltsspells.academic.application.StudentLifecycleService;
import com.theieltsspells.academic.application.dto.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/enrollments")
@RequiredArgsConstructor
@Tag(name = "Admin - Student lifecycle")
@SecurityRequirement(name = "bearerAuth")
public class StudentLifecycleController {
    private final StudentLifecycleService service;

    @PostMapping("/{enrollmentId}/reservations")
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.lifecycle.request')")
    public ReservationResponse requestReservation(@PathVariable UUID enrollmentId, @Valid @RequestBody CreateReservationRequest request) { return service.requestReservation(enrollmentId, request); }
    @GetMapping("/{enrollmentId}/reservations")
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.read')")
    public List<ReservationResponse> reservations(@PathVariable UUID enrollmentId) { return service.reservations(enrollmentId); }
    @PatchMapping("/reservations/{id}/approve")
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.lifecycle.approve')")
    public ReservationResponse approveReservation(@PathVariable UUID id) { return service.approveReservation(id); }
    @PatchMapping("/reservations/{id}/reject")
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.lifecycle.approve')")
    public ReservationResponse rejectReservation(@PathVariable UUID id, @RequestParam(required = false) String reason) { return service.rejectReservation(id, reason); }

    @PostMapping("/{enrollmentId}/transfers")
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.lifecycle.request')")
    public TransferResponse requestTransfer(@PathVariable UUID enrollmentId, @Valid @RequestBody CreateTransferRequest request) { return service.requestTransfer(enrollmentId, request); }
    @GetMapping("/{enrollmentId}/transfers")
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.read')")
    public List<TransferResponse> transfers(@PathVariable UUID enrollmentId) { return service.transfers(enrollmentId); }
    @PatchMapping("/transfers/{id}/approve")
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.lifecycle.approve')")
    public TransferResponse approveTransfer(@PathVariable UUID id) { return service.approveTransfer(id); }
    @PatchMapping("/transfers/{id}/reject")
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.lifecycle.approve')")
    public TransferResponse rejectTransfer(@PathVariable UUID id, @RequestParam(required = false) String reason) { return service.rejectTransfer(id, reason); }
}
