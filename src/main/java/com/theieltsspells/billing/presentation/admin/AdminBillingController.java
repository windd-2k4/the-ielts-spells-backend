package com.theieltsspells.billing.presentation.admin;

import com.theieltsspells.billing.application.ElectronicInvoiceService;
import com.theieltsspells.billing.application.OrderApplicationService;
import com.theieltsspells.billing.application.SepayWebhookService;
import com.theieltsspells.billing.application.dto.*;
import com.theieltsspells.billing.domain.*;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceClient;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
@Tag(name = "Admin - Billing & Invoices")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('admin', 'admissions')")
public class AdminBillingController {

    private final OrderApplicationService orderService;
    private final ElectronicInvoiceService invoiceService;
    private final SepayWebhookService webhookService;
    private final BillingSettingRepository settingsRepository;
    private final SepayEInvoiceClient sepayEInvoiceClient;

    // -------------------------------------------------------------------------
    // 1. ORDERS MANAGEMENT
    // -------------------------------------------------------------------------

    @GetMapping("/orders")
    @Operation(summary = "Danh sách đơn hàng đăng ký khóa học")
    public PageResponse<OrderAdminDto> listOrders(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        String effectiveQuery = query != null ? query : q;
        Page<OrderAdminDto> result = orderService.searchOrders(effectiveQuery, status, courseId, fromDate, toDate, PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Chi tiết đơn hàng")
    public ResponseEntity<OrderAdminDto> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderAdmin(id));
    }

    @PostMapping("/orders/{id}/cancel")
    @Operation(summary = "Hủy đơn hàng chưa thanh toán")
    public ResponseEntity<OrderAdminDto> cancelOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @PostMapping("/orders")
    @Operation(summary = "Tạo đơn tư vấn Zalo / Sinh mã VietQR nhanh cho học viên")
    public ResponseEntity<CheckoutResponse> createOrder(@Valid @RequestBody AdminCreateOrderRequest request) {
        return ResponseEntity.ok(orderService.createAdminOrder(request));
    }

    @GetMapping("/courses-summary")
    @Operation(summary = "Danh sách khóa học tóm tắt cho dropdown tạo đơn tư vấn")
    public ResponseEntity<List<CourseSummaryDto>> getCoursesSummary() {
        return ResponseEntity.ok(orderService.getActiveCoursesSummary());
    }

    @PostMapping("/orders/{id}/confirm-cash")
    @Operation(summary = "Xác nhận thu tiền mặt ngoại lệ tại quầy")
    public ResponseEntity<OrderAdminDto> confirmCashPayment(
            @PathVariable UUID id,
            @RequestParam(required = false) String note
    ) {
        webhookService.confirmManualPayment(id, note);
        return ResponseEntity.ok(orderService.getOrderAdmin(id));
    }

    @PostMapping("/orders/{id}/retry-invoice")
    @Operation(summary = "Thử lại phát hành hóa đơn điện tử cho đơn hàng bị lỗi")
    public ResponseEntity<InvoiceAdminDto> retryInvoiceByOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.retryInvoice(id));
    }

    // -------------------------------------------------------------------------
    // 2. ELECTRONIC INVOICES MANAGEMENT
    // -------------------------------------------------------------------------

    @GetMapping("/invoices")
    @Operation(summary = "Danh sách hóa đơn điện tử theo kỳ kế toán")
    public PageResponse<InvoiceAdminDto> listInvoices(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        String effectiveQuery = query != null ? query : q;
        Page<InvoiceAdminDto> result = invoiceService.searchInvoices(effectiveQuery, status, fromDate, toDate, PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @GetMapping("/invoices/stats")
    @Operation(summary = "Thống kê chỉ số hóa đơn và doanh thu theo kỳ kế toán")
    public ResponseEntity<InvoiceStatsDto> getInvoiceStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return ResponseEntity.ok(invoiceService.getInvoiceStats(fromDate, toDate));
    }

    @GetMapping("/invoices/export")
    @Operation(summary = "Xuất dữ liệu bảng kê hóa đơn điện tử phục vụ báo cáo thuế")
    public ResponseEntity<List<InvoiceAdminDto>> exportInvoices(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        String effectiveQuery = query != null ? query : q;
        return ResponseEntity.ok(invoiceService.exportInvoices(effectiveQuery, status, fromDate, toDate));
    }

    @GetMapping("/invoices/{id}")
    @Operation(summary = "Chi tiết hóa đơn điện tử")
    public ResponseEntity<InvoiceAdminDto> getInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.getInvoiceAdmin(id));
    }

    @PostMapping("/invoices/{id}/retry")
    @Operation(summary = "Thử lại phát hành hóa đơn điện tử lỗi")
    public ResponseEntity<InvoiceAdminDto> retryInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.retryInvoice(id));
    }

    @PostMapping("/invoices/{id}/cancel")
    @Operation(summary = "Hủy hóa đơn điện tử trên Cơ quan Thuế")
    public ResponseEntity<InvoiceAdminDto> cancelInvoice(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason
    ) {
        return ResponseEntity.ok(invoiceService.cancelInvoice(id, reason));
    }

    // -------------------------------------------------------------------------
    // 3. RECONCILIATION & TRANSACTIONS
    // -------------------------------------------------------------------------

    @GetMapping("/reconciliation")
    @Operation(summary = "Danh sách biến động số dư và nhật ký giao dịch SePay")
    public PageResponse<ReconciliationDto> listTransactions(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PaymentTransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        String effectiveQuery = query != null ? query : q;
        Page<ReconciliationDto> result = webhookService.searchTransactions(effectiveQuery, status, fromDate, toDate, PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @PostMapping("/reconciliation/{transactionId}/match")
    @Operation(summary = "Kế toán khớp đơn hàng thủ công cho giao dịch chuyển sai cú pháp")
    public ResponseEntity<ReconciliationDto> matchOrderManually(
            @PathVariable UUID transactionId,
            @RequestParam String orderCode,
            @RequestParam(required = false) String note
    ) {
        return ResponseEntity.ok(webhookService.matchOrderManually(transactionId, orderCode, note));
    }

    // -------------------------------------------------------------------------
    // 4. BILLING SETTINGS
    // -------------------------------------------------------------------------

    @GetMapping("/settings")
    @Operation(summary = "Xem cấu hình SePay và e-Invoice")
    public ResponseEntity<BillingSettingsDto> getSettings() {
        BillingSetting setting = settingsRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        return ResponseEntity.ok(toDto(setting));
    }

    @PutMapping("/settings")
    @Transactional
    @Operation(summary = "Cập nhật cấu hình SePay và e-Invoice")
    public ResponseEntity<BillingSettingsDto> updateSettings(@RequestBody BillingSettingsDto dto) {
        BillingSetting setting = settingsRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        setting.setSepayApiKey(dto.sepayApiKey());
        setting.setSepayWebhookSecret(dto.sepayWebhookSecret());
        setting.setSepayAccountNumber(dto.sepayAccountNumber());
        setting.setSepayBankName(dto.sepayBankName());
        setting.setEinvoiceApiToken(dto.einvoiceApiToken());
        setting.setEinvoiceClientId(dto.einvoiceClientId());
        setting.setEinvoiceClientSecret(dto.einvoiceClientSecret());
        setting.setEinvoiceProviderAccountId(dto.einvoiceProviderAccountId());
        if (dto.einvoiceInvoiceSeries() != null) setting.setEinvoiceInvoiceSeries(dto.einvoiceInvoiceSeries());
        if (dto.einvoiceTemplateCode() != null) setting.setEinvoiceTemplateCode(dto.einvoiceTemplateCode());
        if (dto.einvoiceTaxRate() != null) setting.setEinvoiceTaxRate(dto.einvoiceTaxRate());
        setting.setSellerName(dto.sellerName());
        setting.setSellerTaxCode(dto.sellerTaxCode());
        setting.setSellerAddress(dto.sellerAddress());
        if (dto.isSandbox() != null) setting.setIsSandbox(dto.isSandbox());
        setting.setUpdatedAt(OffsetDateTime.now());
        BillingSetting saved = settingsRepository.save(setting);
        return ResponseEntity.ok(toDto(saved));
    }

    @PostMapping("/settings/test-einvoice")
    @Operation(summary = "Kiểm tra kết nối SePay eInvoice API (Sandbox / Production)")
    public ResponseEntity<SepayEInvoiceClient.ConnectionTestResult> testEinvoiceConnection() {
        BillingSetting setting = settingsRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        return ResponseEntity.ok(sepayEInvoiceClient.testConnection(setting));
    }

    private BillingSettingsDto toDto(BillingSetting s) {
        return new BillingSettingsDto(
                s.getSepayApiKey(),
                s.getSepayWebhookSecret(),
                s.getSepayAccountNumber(),
                s.getSepayBankName(),
                s.getEinvoiceApiToken(),
                s.getEinvoiceClientId(),
                s.getEinvoiceClientSecret(),
                s.getEinvoiceProviderAccountId(),
                s.getEinvoiceInvoiceSeries(),
                s.getEinvoiceTemplateCode(),
                s.getEinvoiceTaxRate(),
                s.getSellerName(),
                s.getSellerTaxCode(),
                s.getSellerAddress(),
                s.getIsSandbox(),
                s.getUpdatedAt()
        );
    }
}
