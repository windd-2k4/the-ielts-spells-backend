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
@PreAuthorize("hasAnyAuthority('admin', 'manager', 'admissions')")
public class StudentLifecycleController {
    private final StudentLifecycleService service;

    @PostMapping("/{enrollmentId}/reservations")
    public ReservationResponse requestReservation(@PathVariable UUID enrollmentId, @Valid @RequestBody CreateReservationRequest request) { return service.requestReservation(enrollmentId, request); }
    @GetMapping("/{enrollmentId}/reservations")
    public List<ReservationResponse> reservations(@PathVariable UUID enrollmentId) { return service.reservations(enrollmentId); }
    @PatchMapping("/reservations/{id}/approve")
    @PreAuthorize("hasAnyAuthority('admin', 'manager')")
    public ReservationResponse approveReservation(@PathVariable UUID id) { return service.approveReservation(id); }

    @PostMapping("/{enrollmentId}/transfers")
    public TransferResponse requestTransfer(@PathVariable UUID enrollmentId, @Valid @RequestBody CreateTransferRequest request) { return service.requestTransfer(enrollmentId, request); }
    @GetMapping("/{enrollmentId}/transfers")
    public List<TransferResponse> transfers(@PathVariable UUID enrollmentId) { return service.transfers(enrollmentId); }
    @PatchMapping("/transfers/{id}/approve")
    @PreAuthorize("hasAnyAuthority('admin', 'manager')")
    public TransferResponse approveTransfer(@PathVariable UUID id) { return service.approveTransfer(id); }
}
