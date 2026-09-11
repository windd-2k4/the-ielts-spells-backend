package com.theieltsspells.admissions.presentation.admin;

import com.theieltsspells.admissions.application.LeadApplicationService;
import com.theieltsspells.admissions.application.dto.ConvertLeadRequest;
import com.theieltsspells.admissions.application.dto.LeadResponse;
import com.theieltsspells.admissions.application.dto.UpdateLeadStatusRequest;
import com.theieltsspells.shared.persistence.enums.LeadStatus;
import com.theieltsspells.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/leads")
@RequiredArgsConstructor
@Tag(name = "Admin - Admissions Leads")
@SecurityRequirement(name = "bearerAuth")
public class LeadAdminController {
    private final LeadApplicationService service;

    @GetMapping
    @PreAuthorize("@permissionPolicy.has(authentication, 'admissions.lead.read')")
    public PageResponse<LeadResponse> list(@RequestParam(defaultValue = "") String q,
                                           @RequestParam(required = false) LeadStatus status,
                                           @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(service.list(q, status, pageable));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@permissionPolicy.has(authentication, 'admissions.lead.manage')")
    public LeadResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateLeadStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @PatchMapping("/{id}/convert")
    @PreAuthorize("@permissionPolicy.has(authentication, 'admissions.lead.convert')")
    public LeadResponse convert(@PathVariable UUID id, @Valid @RequestBody ConvertLeadRequest request) {
        return service.convert(id, request);
    }
}
