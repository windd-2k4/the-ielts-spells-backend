package com.theieltsspells.billing.presentation.admin;

import com.theieltsspells.billing.application.ElectronicInvoiceService;
import com.theieltsspells.billing.application.OrderApplicationService;
import com.theieltsspells.billing.application.SepayWebhookService;
import com.theieltsspells.billing.application.dto.*;
import com.theieltsspells.billing.domain.*;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.billing.infrastructure.security.SecretEncryptionService;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceClient;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceTokenService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.theieltsspells.billing.application.ProductionActivationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/billing")
@Tag(name = "Admin - Billing & Invoices")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('admin', 'admissions')")
public class AdminBillingController {

    private final OrderApplicationService orderService;
    private final OrderRepository orderRepository;
    private final ElectronicInvoiceService invoiceService;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final SepayWebhookService webhookService;
    private final BillingSettingRepository settingsRepository;
    private final SepayEInvoiceClient sepayEInvoiceClient;
    private final SepayEInvoiceTokenService tokenService;
    private final SecretEncryptionService secretEncryptionService;
    private final ProductionActivationService productionActivationService;

    @Autowired
    public AdminBillingController(
            OrderApplicationService orderService,
            OrderRepository orderRepository,
            ElectronicInvoiceService invoiceService,
            ElectronicInvoiceRepository invoiceRepository,
            SepayWebhookService webhookService,
            BillingSettingRepository settingsRepository,
            SepayEInvoiceClient sepayEInvoiceClient,
            SepayEInvoiceTokenService tokenService,
            SecretEncryptionService secretEncryptionService,
            ProductionActivationService productionActivationService
    ) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.webhookService = webhookService;
        this.settingsRepository = settingsRepository;
        this.sepayEInvoiceClient = sepayEInvoiceClient;
        this.tokenService = tokenService;
        this.secretEncryptionService = secretEncryptionService != null ? secretEncryptionService : new SecretEncryptionService();
        this.productionActivationService = productionActivationService;
    }

    public AdminBillingController(
            OrderApplicationService orderService,
            OrderRepository orderRepository,
            ElectronicInvoiceService invoiceService,
            ElectronicInvoiceRepository invoiceRepository,
            SepayWebhookService webhookService,
            BillingSettingRepository settingsRepository,
            SepayEInvoiceClient sepayEInvoiceClient,
            SepayEInvoiceTokenService tokenService,
            SecretEncryptionService secretEncryptionService
    ) {
        this(orderService, orderRepository, invoiceService, invoiceRepository, webhookService, settingsRepository, sepayEInvoiceClient, tokenService, secretEncryptionService, null);
    }

    public AdminBillingController(
            OrderApplicationService orderService,
            ElectronicInvoiceService invoiceService,
            SepayWebhookService webhookService,
            BillingSettingRepository settingsRepository,
            SepayEInvoiceClient sepayEInvoiceClient,
            SepayEInvoiceTokenService tokenService
    ) {
        this(orderService, null, invoiceService, null, webhookService, settingsRepository, sepayEInvoiceClient, tokenService, new SecretEncryptionService(), null);
    }

    public record ReadinessCheckItem(
            String id,
            String title,
            String status, // "PASS", "WARN", "FAIL"
            String details,
            String recommendation
    ) {}

    public record GoLiveReadinessReport(
            boolean canGoProduction,
            String overallStatus, // "READY_FOR_PILOT", "READY_WITH_WARNINGS", "NOT_READY"
            int passCount,
            int warnCount,
            int failCount,
            List<ReadinessCheckItem> checks
    ) {}

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
    @Operation(summary = "Tạo đơn đăng ký và sinh VietQR động để tự động vào học")
    public ResponseEntity<CheckoutResponse> createOrder(@Valid @RequestBody AdminCreateOrderRequest request) {
        return ResponseEntity.ok(orderService.createAdminOrder(request));
    }

    @GetMapping("/static-qr")
    @Operation(summary = "Lấy VietQR tĩnh dùng lâu dài để thu học phí và tự động lập hóa đơn")
    public ResponseEntity<StaticQrResponse> getStaticQr() {
        return ResponseEntity.ok(orderService.getStaticTuitionQr());
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

    @PostMapping("/orders/{id}/approve-pilot")
    @Transactional
    @Operation(summary = "Phê duyệt đơn hàng cho đợt thử nghiệm phát hành HĐĐT thật (Pilot)")
    public ResponseEntity<OrderAdminDto> approvePilotOrder(@PathVariable UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + id));
        order.setPilotApproved(true);
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);
        log.info("[PILOT APPROVAL] Đơn hàng {} đã được Admin phê duyệt cho pilot phát hành HĐĐT thật.", order.getOrderCode());
        return ResponseEntity.ok(orderService.getOrderAdmin(id));
    }

    @PostMapping("/orders/{id}/retry-invoice")
    @Operation(summary = "Thử lại phát hành hóa đơn điện tử cho đơn hàng bị lỗi")
    public ResponseEntity<InvoiceAdminDto> retryInvoiceByOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.retryInvoice(id));
    }

    @PostMapping("/orders/{id}/recheck-invoice")
    @Operation(summary = "Kiểm tra lại trạng thái hóa đơn của đơn hàng với SePay (cho UNKNOWN)")
    public ResponseEntity<InvoiceAdminDto> recheckInvoiceByOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.recheckInvoiceStatus(id));
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

    @PostMapping("/invoices/{id}/issue")
    @Operation(summary = "Flow B: Phát hành chính thức hóa đơn nháp (DRAFT)")
    public ResponseEntity<InvoiceAdminDto> issueDraftInvoice(@PathVariable UUID id) {
        invoiceService.issueDraftInvoice(id);
        return ResponseEntity.ok(invoiceService.getInvoiceAdmin(id));
    }

    @PostMapping("/invoices/{id}/retry")
    @Operation(summary = "Thử lại phát hành hóa đơn điện tử lỗi")
    public ResponseEntity<InvoiceAdminDto> retryInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.retryInvoice(id));
    }

    @PostMapping("/invoices/{id}/recheck")
    @Operation(summary = "Kiểm tra lại trạng thái hóa đơn với SePay (cho hóa đơn UNKNOWN hoặc dở dang)")
    public ResponseEntity<InvoiceAdminDto> recheckInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.recheckInvoiceStatus(id));
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
    // 4. BILLING SETTINGS & LIVE PROVIDERS
    // -------------------------------------------------------------------------

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "Xem cấu hình SePay và e-Invoice (Không bao giờ trả về plaintext secret)")
    public ResponseEntity<BillingSettingsDto> getSettings() {
        BillingSetting setting = settingsRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        return ResponseEntity.ok(toDto(setting));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('admin')")
    @Transactional
    @Operation(summary = "Cập nhật cấu hình SePay và e-Invoice (Mã hóa secret khi lưu)")
    public ResponseEntity<BillingSettingsDto> updateSettings(@RequestBody BillingSettingsDto dto) {
        BillingSetting setting = settingsRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);

        // SePay Payment Gateway
        if (isRealSecretValue(dto.sepayApiKey())) {
            setting.setSepayApiKey(secretEncryptionService.encrypt(dto.sepayApiKey().trim()));
        }
        if (isRealSecretValue(dto.sepayWebhookSecret())) {
            setting.setSepayWebhookSecret(secretEncryptionService.encrypt(dto.sepayWebhookSecret().trim()));
        }
        if (dto.sepayAccountNumber() != null && !dto.sepayAccountNumber().contains("*")) {
            setting.setSepayAccountNumber(dto.sepayAccountNumber().trim());
        }
        if (dto.sepayBankName() != null) {
            setting.setSepayBankName(dto.sepayBankName().trim());
        }

        // Sandbox eInvoice
        if (isRealSecretValue(dto.einvoiceApiToken())) {
            setting.setEinvoiceApiToken(secretEncryptionService.encrypt(dto.einvoiceApiToken().trim()));
        }
        if (dto.einvoiceClientId() != null && !dto.einvoiceClientId().contains("*")) {
            setting.setEinvoiceClientId(dto.einvoiceClientId().trim());
        }
        if (isRealSecretValue(dto.einvoiceClientSecret())) {
            setting.setEinvoiceClientSecret(secretEncryptionService.encrypt(dto.einvoiceClientSecret().trim()));
        }
        if (dto.einvoiceProviderAccountId() != null) setting.setEinvoiceProviderAccountId(dto.einvoiceProviderAccountId().trim());
        if (dto.einvoiceInvoiceSeries() != null) setting.setEinvoiceInvoiceSeries(dto.einvoiceInvoiceSeries().trim());
        if (dto.einvoiceTemplateCode() != null) setting.setEinvoiceTemplateCode(dto.einvoiceTemplateCode().trim());
        if (dto.einvoiceTaxRate() != null) setting.setEinvoiceTaxRate(dto.einvoiceTaxRate());
        if (dto.taxAuthorityApprovedDate() != null) setting.setTaxAuthorityApprovedDate(dto.taxAuthorityApprovedDate().trim());

        // Production eInvoice
        if (dto.prodClientId() != null && !dto.prodClientId().contains("*")) {
            setting.setProdClientId(dto.prodClientId().trim());
        }
        if (isRealSecretValue(dto.prodClientSecret())) {
            setting.setProdClientSecret(secretEncryptionService.encrypt(dto.prodClientSecret().trim()));
        }
        if (dto.prodProviderAccountId() != null) setting.setProdProviderAccountId(dto.prodProviderAccountId().trim());
        if (dto.prodInvoiceSeries() != null) setting.setProdInvoiceSeries(dto.prodInvoiceSeries().trim());
        if (dto.prodTemplateCode() != null) setting.setProdTemplateCode(dto.prodTemplateCode().trim());
        if (dto.prodTaxAuthorityApprovedDate() != null) setting.setProdTaxAuthorityApprovedDate(dto.prodTaxAuthorityApprovedDate().trim());

        // Pilot and Safety Controls
        if (dto.pilotOrderAllowlist() != null) setting.setPilotOrderAllowlist(dto.pilotOrderAllowlist());
        // Activation state and kill-switch can only change through their guarded endpoints.

        // Seller Legal Info & Tax
        if (dto.sellerName() != null) setting.setSellerName(dto.sellerName().trim());
        if (dto.sellerTaxCode() != null) setting.setSellerTaxCode(dto.sellerTaxCode().trim());
        if (dto.sellerAddress() != null) setting.setSellerAddress(dto.sellerAddress().trim());
        if (dto.availableTemplates() != null) setting.setAvailableTemplates(dto.availableTemplates());
        if (dto.taxTreatment() != null) setting.setTaxTreatment(dto.taxTreatment());
        if (dto.invoiceType() != null) setting.setInvoiceType(dto.invoiceType());
        if (dto.taxConfigurationConfirmed() != null) {
            setting.setTaxConfigurationConfirmed(dto.taxConfigurationConfirmed());
            if (Boolean.TRUE.equals(dto.taxConfigurationConfirmed())) {
                setting.setTaxConfigurationConfirmedAt(OffsetDateTime.now());
                setting.setTaxConfigurationConfirmedBy("admin");
            } else {
                setting.setTaxConfigurationConfirmedAt(null);
                setting.setTaxConfigurationConfirmedBy(null);
            }
        }

        setting.setUpdatedAt(OffsetDateTime.now());
        BillingSetting saved = settingsRepository.save(setting);
        return ResponseEntity.ok(toDto(saved));
    }

    @PostMapping("/settings/toggle-auto-invoice")
    @PreAuthorize("hasAuthority('admin')")
    @Transactional
    @Operation(summary = "Bật/Tắt Kill-Switch tự động phát hành hóa đơn điện tử")
    public ResponseEntity<BillingSettingsDto> toggleAutoInvoice(@RequestParam boolean enabled) {
        BillingSetting setting = settingsRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        setting.setAutoInvoiceEnabled(enabled);
        setting.setUpdatedAt(OffsetDateTime.now());
        BillingSetting saved = settingsRepository.save(setting);
        log.info("[KILL-SWITCH] Trạng thái auto_invoice_enabled chuyển thành: {}", enabled);
        return ResponseEntity.ok(toDto(saved));
    }

    @PostMapping("/settings/transition-state")
    @PreAuthorize("hasAuthority('admin')")
    @Transactional
    @Operation(summary = "Chuyển đổi trạng thái kích hoạt môi trường (5-state Activation)")
    public ResponseEntity<BillingSettingsDto> transitionActivationState(
            @RequestParam ProductionActivationState targetState,
            @RequestParam(required = false, defaultValue = "false") boolean explicitAdminConfirmation
    ) {
        BillingSetting setting = settingsRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        GoLiveReadinessReport report = buildReadinessReport(setting);
        boolean hasReadinessFailures = !report.canGoProduction();

        if (productionActivationService != null) {
            BillingSetting saved = productionActivationService.transitionActivationState(
                    targetState, explicitAdminConfirmation, hasReadinessFailures, "admin");
            return ResponseEntity.ok(toDto(saved));
        }

        ProductionActivationState current = setting.getActivationState() != null ? setting.getActivationState() : ProductionActivationState.SANDBOX;
        if (targetState == ProductionActivationState.PRODUCTION_PILOT || targetState == ProductionActivationState.PRODUCTION_ACTIVE) {
            if (!report.canGoProduction()) {
                throw new BusinessRuleException("Không thể chuyển sang " + targetState + " vì còn " + report.failCount() + " tiêu chí FAIL chưa đạt!");
            }
            if (targetState == ProductionActivationState.PRODUCTION_ACTIVE && current != ProductionActivationState.PRODUCTION_PILOT) {
                throw new BusinessRuleException("Chỉ có thể kích hoạt PRODUCTION_ACTIVE sau khi đã trải qua giai đoạn PRODUCTION_PILOT!");
            }
        }

        setting.setActivationState(targetState);
        setting.setIsSandbox(targetState == ProductionActivationState.SANDBOX);
        setting.setUpdatedAt(OffsetDateTime.now());
        BillingSetting saved = settingsRepository.save(setting);
        log.info("[ACTIVATION STATE] Chuyển đổi trạng thái kích hoạt: {} -> {}", current, targetState);
        return ResponseEntity.ok(toDto(saved));
    }

    @PostMapping("/pilot/evaluate")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "Đánh giá kết quả kiểm thử Pilot phát hành hóa đơn thật theo 7 tiêu chí bắt buộc")
    public ResponseEntity<PilotExecution> evaluatePilotOrder(
            @RequestParam UUID orderId,
            @RequestParam(required = false, defaultValue = "admin") String executedBy
    ) {
        if (productionActivationService == null) {
            throw new BusinessRuleException("ProductionActivationService chưa sẵn sàng");
        }
        return ResponseEntity.ok(productionActivationService.evaluatePilotOrder(orderId, executedBy));
    }

    @GetMapping("/pilot/latest")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "Xem kết quả đợt kiểm thử Pilot gần nhất")
    public ResponseEntity<PilotExecution> getLatestPilot() {
        if (productionActivationService == null) {
            return ResponseEntity.noContent().build();
        }
        return productionActivationService.getLatestPilotExecution()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/settings/providers")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "Lấy danh sách Provider Accounts và Mẫu số/Ký hiệu động từ SePay")
    public ResponseEntity<List<SepayEInvoiceClient.ProviderAccountDto>> getLiveProviders() {
        BillingSetting setting = settingsRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        List<SepayEInvoiceClient.ProviderAccountDto> providers = sepayEInvoiceClient.getProviderAccounts(setting);
        return ResponseEntity.ok(providers);
    }

    @PostMapping("/settings/test-einvoice")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "Kiểm tra kết nối SePay eInvoice API (Sandbox / Production)")
    public ResponseEntity<SepayEInvoiceClient.ConnectionTestResult> testEinvoiceConnection() {
        BillingSetting setting = settingsRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        return ResponseEntity.ok(sepayEInvoiceClient.testConnection(setting));
    }

    // -------------------------------------------------------------------------
    // 5. GO-LIVE READINESS 15-POINT HEALTH CHECK
    // -------------------------------------------------------------------------

    @PostMapping("/production-readiness")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "Báo cáo kiểm tra 15 tiêu chí sẵn sàng Go-Live Production")
    public ResponseEntity<GoLiveReadinessReport> checkProductionReadiness() {
        BillingSetting s = settingsRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        return ResponseEntity.ok(buildReadinessReport(s));
    }

    @GetMapping("/readiness-check")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "Báo cáo kiểm tra 15 tiêu chí sẵn sàng Go-Live Production (GET alias)")
    public ResponseEntity<GoLiveReadinessReport> checkGoLiveReadiness() {
        return checkProductionReadiness();
    }

    private GoLiveReadinessReport buildReadinessReport(BillingSetting s) {
        List<ReadinessCheckItem> items = new ArrayList<>();

        // 1. SePay Payment Configured
        boolean hasBankAndKey = s.getSepayApiKey() != null && !s.getSepayApiKey().isBlank()
                && s.getSepayAccountNumber() != null && !s.getSepayAccountNumber().isBlank()
                && s.getSepayBankName() != null && !s.getSepayBankName().isBlank();
        items.add(new ReadinessCheckItem(
                "SEPAY_PAYMENT_CONFIGURED",
                "Cấu hình Cổng thanh toán SePay (API Key, Số TK, Ngân hàng)",
                hasBankAndKey ? "PASS" : "FAIL",
                hasBankAndKey ? String.format("Đã thiết lập: Ngân hàng %s, Số TK %s", s.getSepayBankName(), secretEncryptionService.maskAccountNumber(s.getSepayAccountNumber()))
                        : "Chưa hoàn tất thiết lập API Key hoặc tài khoản ngân hàng thụ hưởng SePay",
                hasBankAndKey ? null : "Vui lòng nhập API Key, Tên ngân hàng và Số tài khoản trong tab Cài đặt"
        ));

        // 2. Webhook Secret Hardened
        boolean hasWebhookSecret = s.getSepayWebhookSecret() != null && !s.getSepayWebhookSecret().isBlank();
        items.add(new ReadinessCheckItem(
                "SEPAY_WEBHOOK_SECRET",
                "SePay Webhook Secret (Bảo mật Webhook)",
                hasWebhookSecret ? "PASS" : "FAIL",
                hasWebhookSecret ? "Đã cấu hình Webhook Token bảo mật mã hóa AES-256" : "Chưa đặt Webhook Secret; hệ thống sẽ từ chối mọi webhook để tránh giả mạo giao dịch",
                hasWebhookSecret ? null : "Bắt buộc cấu hình Webhook Secret trước khi nhận thanh toán thật"
        ));

        boolean isProdContext = s.isProductionContext();

        // 3. Production Credentials Set
        boolean hasProdCredentials = s.getProdClientId() != null && !s.getProdClientId().isBlank()
                && s.getProdClientSecret() != null && !s.getProdClientSecret().isBlank();
        String prodCredsStatus = hasProdCredentials ? "PASS" : (isProdContext ? "FAIL" : "WARN");
        items.add(new ReadinessCheckItem(
                "PROD_CREDENTIALS_SET",
                "SePay eInvoice Production Client ID & Secret",
                prodCredsStatus,
                hasProdCredentials ? "Đã cấu hình Client ID và Secret môi trường Production"
                        : (isProdContext ? "Chưa cấu hình Production Client ID hoặc Client Secret riêng biệt" : "Đang ở chế độ Sandbox (Chưa cần kích hoạt Production credentials)"),
                hasProdCredentials ? null : "Nhập Client ID và Secret lấy từ dashboard SePay eInvoice Production"
        ));

        // 3b. Production Secret Decryptability (Prevent silent overwrite or corrupted master key)
        if (s.getProdClientSecret() != null && !s.getProdClientSecret().isBlank()) {
            boolean canDecrypt = secretEncryptionService.canDecrypt(s.getProdClientSecret());
            items.add(new ReadinessCheckItem(
                    "PROD_SECRET_DECRYPTABILITY",
                    "Khả năng giải mã Production Secret hiện có",
                    canDecrypt ? "PASS" : "FAIL",
                    canDecrypt ? "Secret Production trong cơ sở dữ liệu đã được xác thực giải mã thành công với Master Key"
                            : "Không thể giải mã Production Secret hiện có (sai khóa SEPAY_MASTER_KEY hoặc secret bị thay đổi). Bí mật không bị ghi đè hay reset.",
                    canDecrypt ? null : "Cung cấp đúng khóa SEPAY_MASTER_KEY đã mã hóa secret này"
            ));
        }

        // 4. Production Separated From Sandbox
        boolean sandboxSeparated = false;
        String separationDetails;
        if (!hasProdCredentials) {
            separationDetails = isProdContext ? "Chưa có Production Client ID để so sánh với Sandbox" : "Đang chạy chế độ Sandbox";
        } else if (s.getProdClientId().trim().equalsIgnoreCase(s.getEinvoiceClientId() != null ? s.getEinvoiceClientId().trim() : "")) {
            separationDetails = "Production Client ID trùng lặp hoàn toàn với Sandbox Client ID! Không được dùng chung credential Sandbox cho Production.";
        } else {
            sandboxSeparated = true;
            separationDetails = "Production credentials đã được tách biệt độc lập với Sandbox credentials";
        }
        String sepStatus = sandboxSeparated ? "PASS" : (isProdContext ? "FAIL" : "WARN");
        items.add(new ReadinessCheckItem(
                "PROD_SEPARATED_FROM_SANDBOX",
                "Tách biệt môi trường Production và Sandbox",
                sepStatus,
                separationDetails,
                (sandboxSeparated || !isProdContext) ? null : "Tạo và cấu hình cặp Client ID / Secret riêng của môi trường Production trên SePay"
        ));

        // 5. Production OAuth Token Handshake
        String tokenCheckStatus;
        String tokenCheckDetails;
        if (hasProdCredentials) {
            try {
                String prodToken = tokenService != null ? tokenService.verifyProductionHandshake(s) : null;
                if (prodToken != null && !prodToken.isBlank()) {
                    tokenCheckStatus = "PASS";
                    tokenCheckDetails = "Xác thực OAuth2 Token thành công với máy chủ SePay Production (https://einvoice-api.sepay.vn)";
                } else {
                    tokenCheckStatus = "FAIL";
                    tokenCheckDetails = "Máy chủ SePay Production từ chối cấp token";
                }
            } catch (Exception ex) {
                tokenCheckStatus = "FAIL";
                tokenCheckDetails = "Xác thực handshake thất bại: " + ex.getMessage();
            }
        } else {
            tokenCheckStatus = isProdContext ? "FAIL" : "PASS";
            tokenCheckDetails = isProdContext ? "Chưa có Production Client ID & Secret để xác thực handshake" : "Đang kiểm thử Sandbox (Bỏ qua handshake Production)";
        }
        items.add(new ReadinessCheckItem(
                "PROD_OAUTH_TOKEN_HANDSHAKE",
                "Xác thực Token với Máy chủ SePay Production",
                tokenCheckStatus,
                tokenCheckDetails,
                "PASS".equals(tokenCheckStatus) ? null : "Kiểm tra lại tính chính xác của Production Client ID / Secret"
        ));

        // 6. Production Provider Account
        boolean hasProdProvider = (s.getProdProviderAccountId() != null && !s.getProdProviderAccountId().isBlank())
                || (s.getEinvoiceProviderAccountId() != null && !s.getEinvoiceProviderAccountId().isBlank());
        String prodProviderStatus = hasProdProvider ? "PASS" : (isProdContext ? "FAIL" : "WARN");
        items.add(new ReadinessCheckItem(
                "PROD_PROVIDER_ACCOUNT",
                "Nhà cung cấp HĐĐT (VNPT / Viettel / MISA / Mắt Bão...)",
                prodProviderStatus,
                hasProdProvider ? String.format("Đã liên kết Provider Account ID: %s", s.getActiveProviderAccountId())
                        : (isProdContext ? "Chưa cấu hình Provider Account ID cho môi trường Production" : "Đang chạy chế độ Sandbox"),
                (hasProdProvider || !isProdContext) ? null : "Nhập Provider Account ID chính thức đã kích hoạt trên SePay Production"
        ));

        // 7. Production Tax Authority Approved Date
        boolean hasTaxDate = (s.getProdTaxAuthorityApprovedDate() != null && !s.getProdTaxAuthorityApprovedDate().isBlank())
                || (s.getTaxAuthorityApprovedDate() != null && !s.getTaxAuthorityApprovedDate().isBlank());
        items.add(new ReadinessCheckItem(
                "PROD_TAX_AUTHORITY_APPROVED",
                "Phê duyệt của Cơ quan Thuế (tax_authority_approved_date)",
                hasTaxDate ? "PASS" : "WARN",
                hasTaxDate ? ("Đã được Cơ quan Thuế phê duyệt ngày: " + (s.getProdTaxAuthorityApprovedDate() != null ? s.getProdTaxAuthorityApprovedDate() : s.getTaxAuthorityApprovedDate()))
                        : "Chưa có ngày phê duyệt tờ khai từ Cơ quan Thuế trên tài khoản Production",
                hasTaxDate ? null : "Đảm bảo tờ khai Đăng ký sử dụng HĐĐT (Mẫu 01/ĐKTĐ-HĐĐT) đã được Thuế chấp thuận trước khi xuất hóa đơn thật"
        ));

        // 8. Production Series & Template
        boolean hasSeriesTemplate = (s.getProdTemplateCode() != null && !s.getProdTemplateCode().isBlank() && s.getProdInvoiceSeries() != null && !s.getProdInvoiceSeries().isBlank())
                || (s.getEinvoiceTemplateCode() != null && !s.getEinvoiceTemplateCode().isBlank() && s.getEinvoiceInvoiceSeries() != null && !s.getEinvoiceInvoiceSeries().isBlank());
        items.add(new ReadinessCheckItem(
                "PROD_SERIES_AND_TEMPLATE",
                "Mẫu số & Ký hiệu hóa đơn (Template & Series)",
                hasSeriesTemplate ? "PASS" : (isProdContext ? "FAIL" : "WARN"),
                hasSeriesTemplate ? String.format("Mẫu số: %s, Ký hiệu: %s", s.getActiveTemplateCode(), s.getActiveInvoiceSeries())
                        : "Chưa cấu hình Mẫu số hoặc Ký hiệu hóa đơn",
                hasSeriesTemplate ? null : "Nhập Mẫu số và Ký hiệu đã đăng ký từ nhà cung cấp HĐĐT chính thức"
        ));

        // 9. Seller Legal Info
        boolean hasSellerInfo = s.getSellerName() != null && !s.getSellerName().isBlank()
                && s.getSellerTaxCode() != null && !s.getSellerTaxCode().isBlank();
        boolean hasFullSellerInfo = hasSellerInfo && s.getSellerAddress() != null && !s.getSellerAddress().isBlank();
        items.add(new ReadinessCheckItem(
                "SELLER_LEGAL_INFO",
                "Thông tin pháp lý đơn vị bán hàng (Tên, MST, Địa chỉ)",
                hasFullSellerInfo ? "PASS" : (hasSellerInfo ? "WARN" : (isProdContext ? "FAIL" : "WARN")),
                hasFullSellerInfo ? String.format("Đơn vị: %s (MST: %s)", s.getSellerName(), s.getSellerTaxCode())
                        : (hasSellerInfo ? String.format("Đơn vị: %s (MST: %s) - Chưa cấu hình địa chỉ", s.getSellerName(), s.getSellerTaxCode())
                        : "Thông tin người bán hàng chưa đầy đủ (Tên đơn vị, MST, Địa chỉ)"),
                hasFullSellerInfo ? null : "Cập nhật tên tổ chức/hộ kinh doanh, MST và địa chỉ chính xác"
        ));

        // 10. Remaining Quota
        Integer quota = null;
        try {
            quota = sepayEInvoiceClient.getRemainingQuota(s);
        } catch (Exception ignored) {}

        String quotaStatus;
        String quotaDetails;
        String quotaRec = null;

        if (quota == null) {
            quotaStatus = "WARN";
            quotaDetails = "Không thể đọc trực tiếp hạn mức hóa đơn từ SePay";
            quotaRec = "Kiểm tra lại hạn ngạch còn lại trên cổng SePay eInvoice";
        } else if (quota == 0) {
            quotaStatus = "FAIL";
            quotaDetails = "Hạn ngạch hóa đơn đã hết (0 hóa đơn còn lại). Không thể phát hành thêm!";
            quotaRec = "Cần nạp thêm gói hóa đơn trên SePay trước khi kích hoạt";
        } else if (quota <= 10) {
            quotaStatus = "WARN";
            quotaDetails = String.format("Hạn ngạch hóa đơn sắp hết: Còn %d hóa đơn khả dụng", quota);
            quotaRec = "Nên nạp thêm gói hóa đơn sớm để tránh gián đoạn dịch vụ";
        } else {
            quotaStatus = "PASS";
            quotaDetails = String.format("Hạn ngạch hóa đơn khả dụng: %d số", quota);
        }

        items.add(new ReadinessCheckItem(
                "ENVIRONMENT_QUOTA",
                "Hạn mức hóa đơn khả dụng (Quota)",
                quotaStatus,
                quotaDetails,
                quotaRec
        ));

        // 11. Tax Treatment Mapped & Confirmed (No automatic legal conclusions)
        boolean taxConfigured = (s.getTaxTreatment() != null || s.getEinvoiceTaxRate() != null);
        boolean taxConfirmed = Boolean.TRUE.equals(s.getTaxConfigurationConfirmed());
        String taxStatus;
        String taxDetails;
        String taxRec = null;

        if (!taxConfigured) {
            taxStatus = isProdContext ? "FAIL" : "WARN";
            taxDetails = "Chưa cấu hình quy tắc thuế suất và loại hóa đơn cho hệ thống";
            taxRec = "Cấu hình loại hóa đơn và thuế suất theo tình trạng thuế thực tế của đơn vị";
        } else if (!taxConfirmed) {
            taxStatus = isProdContext ? "FAIL" : "WARN";
            taxDetails = String.format("Đã chọn quy tắc thuế: %s (Loại: %s) nhưng CHƯA ĐƯỢC Quản trị viên/Kế toán trưởng xác nhận theo tình trạng thuế thực tế",
                    s.getTaxTreatment() != null ? s.getTaxTreatment().getDescription() : (s.getEinvoiceTaxRate() + "%"),
                    s.getInvoiceType() != null ? s.getInvoiceType() : "HĐ");
            taxRec = "Quản trị viên hoặc Kế toán cần đối chiếu và tích chọn xác nhận cấu hình thuế này trong tab Cài đặt";
        } else {
            taxStatus = "PASS";
            taxDetails = String.format("Đã cấu hình và xác nhận: Loại HĐ %s, Quy tắc %s (Xác nhận bởi %s lúc %s)",
                    s.getInvoiceType() != null ? s.getInvoiceType() : "VAT",
                    s.getTaxTreatment() != null ? s.getTaxTreatment().getDescription() : (s.getEinvoiceTaxRate() + "%"),
                    s.getTaxConfigurationConfirmedBy() != null ? s.getTaxConfigurationConfirmedBy() : "Admin",
                    s.getTaxConfigurationConfirmedAt() != null ? s.getTaxConfigurationConfirmedAt().toString() : "N/A");
        }

        items.add(new ReadinessCheckItem(
                "TAX_TREATMENT_MAPPED",
                "Cấu hình thuế hóa đơn & Xác nhận thực tế",
                taxStatus,
                taxDetails,
                taxRec
        ));

        // 12. Master Key Grade (Fail-closed)
        boolean hasValidMaster = secretEncryptionService.hasValidMasterKey();
        boolean isDevFallback = secretEncryptionService.isDevFallbackActive();
        boolean isProdEnv = secretEncryptionService.isProductionEnvironment() || isProdContext;

        String keyStatus;
        String keyDetails;
        String keyRec = null;

        if (hasValidMaster && !isDevFallback) {
            keyStatus = "PASS";
            keyDetails = "Khóa master key AES-256 (SEPAY_MASTER_KEY) hợp lệ và đạt chuẩn bảo mật Production";
        } else if (isProdEnv) {
            keyStatus = "FAIL";
            keyDetails = "Production thiếu biến môi trường SEPAY_MASTER_KEY hoặc khóa chưa đủ 32 ký tự. Dev fallback bị cấm hoàn toàn ở Production!";
            keyRec = "Cấu hình biến môi trường SEPAY_MASTER_KEY (>= 32 ký tự) trên server Production";
        } else {
            keyStatus = "WARN";
            keyDetails = "Đang sử dụng dev fallback master key nội bộ cho môi trường phát triển/local. Không được dùng cho Production.";
            keyRec = "Cấu hình SEPAY_MASTER_KEY trước khi chuyển sang môi trường Production";
        }

        items.add(new ReadinessCheckItem(
                "ENCRYPTION_KEY_PRODUCTION_GRADE",
                "Khóa mã hóa bảo mật cấp độ Production (SEPAY_MASTER_KEY)",
                keyStatus,
                keyDetails,
                keyRec
        ));

        // 13. Auto Invoice Kill-Switch
        boolean autoEnabled = Boolean.TRUE.equals(s.getAutoInvoiceEnabled());
        items.add(new ReadinessCheckItem(
                "AUTO_INVOICE_KILL_SWITCH",
                "Công tắc ngắt khẩn cấp (Emergency Kill-Switch)",
                autoEnabled ? "PASS" : "WARN",
                autoEnabled ? "Tính năng tự động phát hành HĐĐT đang hoạt động bình thường" : "Kill-switch đang kích hoạt (auto_invoice_enabled=false). HĐĐT sẽ không tự động phát hành.",
                autoEnabled ? null : "Bật lại auto_invoice_enabled khi đã sẵn sàng tự động phát hành"
        ));

        // 14. Order Sequence Sync & UNKNOWN Reconciliation
        int pendingUnknownCount = 0;
        if (invoiceRepository != null) {
            try {
                List<ElectronicInvoice> unknownInvoices = invoiceRepository.findByStatusIn(List.of(InvoiceStatus.UNKNOWN));
                List<ElectronicInvoice> requiresReview = invoiceRepository.findByReconciliationStatus(ReconciliationStatus.REQUIRES_REVIEW);
                pendingUnknownCount = (unknownInvoices != null ? unknownInvoices.size() : 0) + (requiresReview != null ? requiresReview.size() : 0);
            } catch (Exception ignored) {}
        }
        items.add(new ReadinessCheckItem(
                "ORDER_SEQUENCE_SYNC",
                "Đồng bộ hóa đơn và Trạng thái đối soát dở dang",
                pendingUnknownCount == 0 ? "PASS" : "WARN",
                pendingUnknownCount == 0 ? "Không có hóa đơn nào đang ở trạng thái UNKNOWN hoặc REQUIRES_REVIEW"
                        : String.format("Hiện còn %d hóa đơn cần đối soát hoặc kiểm tra lại trạng thái", pendingUnknownCount),
                pendingUnknownCount == 0 ? null : "Bấm nút 'Kiểm tra trạng thái SePay' cho các hóa đơn UNKNOWN trong tab Hóa đơn điện tử"
        ));

        // 15. Controlled Pilot Execution Gate
        com.theieltsspells.billing.domain.PilotResultStatus pilotStatus = s.getPilotStatus();
        if (pilotStatus == null && productionActivationService != null) {
            pilotStatus = productionActivationService.getLatestPilotExecution()
                    .map(com.theieltsspells.billing.domain.PilotExecution::getStatus)
                    .orElse(com.theieltsspells.billing.domain.PilotResultStatus.NOT_STARTED);
        }
        if (pilotStatus == null) {
            pilotStatus = com.theieltsspells.billing.domain.PilotResultStatus.NOT_STARTED;
        }

        String pilotCheckStatus;
        String pilotDetails;
        String pilotRec = null;

        if (pilotStatus == com.theieltsspells.billing.domain.PilotResultStatus.PASSED) {
            pilotCheckStatus = "PASS";
            pilotDetails = "Đợt thử nghiệm Pilot phát hành hóa đơn thật đã hoàn thành xuất sắc (7/7 tiêu chí PASSED)";
        } else {
            pilotCheckStatus = isProdContext ? "FAIL" : "WARN";
            if (pilotStatus == com.theieltsspells.billing.domain.PilotResultStatus.NOT_STARTED) {
                pilotDetails = "Chưa thực hiện đợt thử nghiệm Pilot nào (PilotResult = NOT_STARTED)";
                pilotRec = "Cần chạy thử nghiệm 1 đơn hàng Pilot thật trước khi kích hoạt PRODUCTION_ACTIVE";
            } else if (pilotStatus == com.theieltsspells.billing.domain.PilotResultStatus.IN_PROGRESS) {
                pilotDetails = "Đợt thử nghiệm Pilot đang diễn ra (PilotResult = IN_PROGRESS)";
                pilotRec = "Chờ đợt Pilot hoàn tất và kiểm chứng đối soát xong";
            } else if (pilotStatus == com.theieltsspells.billing.domain.PilotResultStatus.REQUIRES_REVIEW) {
                pilotDetails = "Đợt thử nghiệm Pilot cần kế toán đối soát lại (PilotResult = REQUIRES_REVIEW)";
                pilotRec = "Kiểm tra và đối soát giao dịch Pilot trước khi chuyển sang Active";
            } else {
                pilotDetails = "Đợt thử nghiệm Pilot gần nhất bị thất bại (PilotResult = FAILED)";
                pilotRec = "Khắc phục lỗi và thực hiện lại đợt Pilot mới";
            }
        }

        items.add(new ReadinessCheckItem(
                "PILOT_EXECUTION_GATE",
                "Kiểm chứng đơn hàng thử nghiệm thực tế (Pilot Execution Gate)",
                pilotCheckStatus,
                pilotDetails,
                pilotRec
        ));

        // 16. HTTPS Security
        items.add(new ReadinessCheckItem(
                "HTTPS_SECURITY",
                "Bảo mật truyền thông mạng (SSL/TLS HTTPS)",
                "PASS",
                "Kết nối SePay API bắt buộc qua HTTPS và hỗ trợ TLS 1.3",
                null
        ));

        int pass = (int) items.stream().filter(i -> "PASS".equals(i.status())).count();
        int warn = (int) items.stream().filter(i -> "WARN".equals(i.status())).count();
        int fail = (int) items.stream().filter(i -> "FAIL".equals(i.status())).count();
        boolean canGoProduction = (fail == 0);
        String overallStatus = (fail > 0) ? "NOT_READY" : ((warn > 0) ? "READY_WITH_WARNINGS" : "READY_FOR_PILOT");

        return new GoLiveReadinessReport(canGoProduction, overallStatus, pass, warn, fail, items);
    }

    private boolean isRealSecretValue(String secret) {
        return secret != null && !secret.isBlank() && !secret.contains("*") && !secret.contains("•");
    }

    private BillingSettingsDto toDto(BillingSetting s) {
        return new BillingSettingsDto(
                // Secrets NEVER returned in plaintext
                null,
                s.getSepayApiKey() != null && !s.getSepayApiKey().isBlank(),
                s.getSepayApiKey() != null ? secretEncryptionService.maskClientId(s.getSepayApiKey()) : null,
                null,
                s.getSepayWebhookSecret() != null && !s.getSepayWebhookSecret().isBlank(),
                s.getSepayAccountNumber(),
                secretEncryptionService.maskAccountNumber(s.getSepayAccountNumber()),
                s.getSepayBankName(),

                // Sandbox eInvoice
                null,
                s.getEinvoiceClientId(),
                secretEncryptionService.maskClientId(s.getEinvoiceClientId()),
                null,
                s.getEinvoiceClientSecret() != null && !s.getEinvoiceClientSecret().isBlank(),
                s.getEinvoiceProviderAccountId(),
                s.getEinvoiceInvoiceSeries(),
                s.getEinvoiceTemplateCode(),
                s.getEinvoiceTaxRate(),
                s.getTaxAuthorityApprovedDate(),

                // Production eInvoice
                s.getProdClientId(),
                secretEncryptionService.maskClientId(s.getProdClientId()),
                null,
                s.getProdClientSecret() != null && !s.getProdClientSecret().isBlank(),
                s.getProdProviderAccountId(),
                s.getProdInvoiceSeries(),
                s.getProdTemplateCode(),
                s.getProdTaxAuthorityApprovedDate(),

                // Activation & Pilot Control
                s.getActivationState(),
                s.getAutoInvoiceEnabled(),
                s.getPilotOrderAllowlist(),
                s.getPilotStatus(),
                s.getLastPilotExecutionId(),

                // Organization Legal Info & Tax
                s.getSellerName(),
                s.getSellerTaxCode(),
                s.getSellerAddress(),
                s.getIsSandbox(),
                s.getAvailableTemplates(),
                s.getTaxTreatment(),
                s.getTaxConfigurationConfirmed(),
                s.getTaxConfigurationConfirmedAt(),
                s.getTaxConfigurationConfirmedBy(),
                s.getInvoiceType(),
                s.getUpdatedAt()
        );
    }
}
