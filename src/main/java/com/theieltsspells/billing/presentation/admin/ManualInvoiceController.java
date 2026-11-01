package com.theieltsspells.billing.presentation.admin;

import com.theieltsspells.billing.application.ManualInvoiceApplicationService;
import com.theieltsspells.billing.application.dto.InvoiceAdminDto;
import com.theieltsspells.billing.application.dto.ManualInvoiceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/billing/manual-invoices")
@Tag(name = "Admin - Manual Invoices")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('admin', 'admissions')")
public class ManualInvoiceController {

    private final ManualInvoiceApplicationService service;

    public ManualInvoiceController(ManualInvoiceApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Tạo giao dịch thủ công và xếp hàng tạo hóa đơn nháp")
    public ResponseEntity<InvoiceAdminDto> create(
            @Valid @RequestBody ManualInvoiceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.createDraft(request, authentication.getName()));
    }
}
