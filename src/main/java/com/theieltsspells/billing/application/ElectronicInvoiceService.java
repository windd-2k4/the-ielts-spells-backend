package com.theieltsspells.billing.application;

import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.billing.application.dto.InvoiceAdminDto;
import com.theieltsspells.billing.application.dto.InvoiceStatsDto;
import com.theieltsspells.billing.domain.*;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.InvoiceAuditLogRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceClient;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ElectronicInvoiceService {

    private final ElectronicInvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final BillingSettingRepository billingSettingRepository;
    private final SepayEInvoiceClient sepayEInvoiceClient;
    private final CourseRepository courseRepository;
    private final EmailBillingNotificationService emailService;
    private final InvoiceAuditLogRepository auditLogRepository;

    /**
     * Khởi tạo hoặc lấy Hóa đơn điện tử liên kết với đơn hàng
     * Gán mã reference_code duy nhất bất biến: INV-{orderCode}
     */
    @Transactional
    public ElectronicInvoice initOrGetInvoice(Order order) {
        Optional<ElectronicInvoice> existing = invoiceRepository.findLatestByOrderIdForUpdate(order.getId());
        if (existing.isEmpty()) {
            existing = invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId());
        }
        if (existing.isPresent()) {
            return existing.get();
        }

        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);
        TaxTreatment taxTreatment = settings.getTaxTreatment() != null ? settings.getTaxTreatment() : TaxTreatment.NOT_SUBJECT_TO_VAT;
        Integer mappedTaxRate = taxTreatment.getSepayTaxRate();

        String referenceCode = "INV-" + order.getOrderCode();

        ElectronicInvoice inv = new ElectronicInvoice();
        inv.setOrderId(order.getId());
        inv.setReferenceCode(referenceCode);
        inv.setStatus(InvoiceStatus.PENDING_ISSUE);
        inv.setReconciliationStatus(ReconciliationStatus.NOT_REQUIRED);
        inv.setIsDraft(false);
        inv.setBuyerType(order.getBuyerType() == InvoiceBuyerType.BUSINESS ? "enterprise" : "personal");
        inv.setBuyerName(order.getCustomerName());
        inv.setBuyerLegalName(order.getInvoiceCompanyName());
        inv.setBuyerTaxCode(order.getInvoiceTaxCode());
        inv.setBuyerAddress(order.getInvoiceAddress() != null && !order.getInvoiceAddress().isBlank()
                ? order.getInvoiceAddress() : "Việt Nam");
        inv.setBuyerEmail(order.getInvoiceEmail() != null && !order.getInvoiceEmail().isBlank()
                ? order.getInvoiceEmail() : order.getCustomerEmail());
        inv.setBuyerPhone(order.getCustomerPhone());
        inv.setPaymentMethod("CK");
        inv.setSubtotal(order.getAmount());
        inv.setTaxTreatment(taxTreatment);
        inv.setTaxRate(mappedTaxRate);
        inv.setTaxAmount(BigDecimal.ZERO);
        inv.setTotalAmount(order.getAmount());
        inv.setRetryCount(0);
        inv.setCreatedAt(OffsetDateTime.now());
        inv.setUpdatedAt(OffsetDateTime.now());

        try {
            ElectronicInvoice saved = invoiceRepository.save(inv);
            recordAudit(saved.getId(), "INVOICE_REQUESTED", "SYSTEM", "Khởi tạo bản ghi HĐĐT cho đơn " + order.getOrderCode(), null);
            return saved;
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            log.warn("Xung đột đồng thời khi tạo HĐĐT cho đơn {}. Truy xuất lại bản ghi từ CSDL...", order.getOrderCode());
            return invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                    .orElseThrow(() -> new IllegalStateException("Không thể khởi tạo hoặc truy xuất hóa đơn cho đơn " + order.getOrderCode(), dive));
        }
    }

    /**
     * Khởi tạo hóa đơn theo đúng khoản tiền thực nhận từ QR tĩnh. Một đơn hàng
     * có thể có nhiều giao dịch cọc/thanh toán và vì vậy có nhiều hóa đơn.
     */
    @Transactional
    public ElectronicInvoice initOrGetInvoice(PaymentTransaction transaction, Order order) {
        if (transaction == null || transaction.getId() == null) {
            throw new BusinessRuleException("Không thể lập hóa đơn khi giao dịch thanh toán chưa được lưu");
        }

        Optional<ElectronicInvoice> existing = invoiceRepository.findByPaymentTransactionIdForUpdate(transaction.getId());
        if (existing.isEmpty()) {
            existing = invoiceRepository.findByPaymentTransactionId(transaction.getId());
        }
        if (existing.isPresent()) {
            ElectronicInvoice invoice = existing.get();
            if (order != null && invoice.getOrderId() == null) {
                invoice.setOrderId(order.getId());
                if (invoice.getBuyerEmail() == null || invoice.getBuyerEmail().isBlank()) {
                    invoice.setBuyerEmail(order.getInvoiceEmail() != null && !order.getInvoiceEmail().isBlank()
                            ? order.getInvoiceEmail() : order.getCustomerEmail());
                }
                invoice.setUpdatedAt(OffsetDateTime.now());
                return invoiceRepository.save(invoice);
            }
            return invoice;
        }

        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);
        TaxTreatment taxTreatment = settings.getTaxTreatment() != null
                ? settings.getTaxTreatment()
                : TaxTreatment.NOT_SUBJECT_TO_VAT;

        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setOrderId(order != null ? order.getId() : null);
        invoice.setPaymentTransactionId(transaction.getId());
        invoice.setReferenceCode("PAY-" + transaction.getId());
        invoice.setProductName("Đóng học phí đào tạo IELTS");
        invoice.setStatus(InvoiceStatus.PENDING_ISSUE);
        invoice.setReconciliationStatus(ReconciliationStatus.NOT_REQUIRED);
        invoice.setIsDraft(false);
        invoice.setBuyerType(order != null && order.getBuyerType() == InvoiceBuyerType.BUSINESS
                ? "enterprise" : "personal");
        invoice.setBuyerName(transaction.getPayerName() != null && !transaction.getPayerName().isBlank()
                ? transaction.getPayerName() : "Người nộp học phí");
        invoice.setBuyerLegalName(order != null ? order.getInvoiceCompanyName() : null);
        invoice.setBuyerTaxCode(order != null ? order.getInvoiceTaxCode() : null);
        invoice.setBuyerAddress(order != null && order.getInvoiceAddress() != null && !order.getInvoiceAddress().isBlank()
                ? order.getInvoiceAddress() : "Việt Nam");
        invoice.setBuyerEmail(order != null
                ? (order.getInvoiceEmail() != null && !order.getInvoiceEmail().isBlank()
                    ? order.getInvoiceEmail() : order.getCustomerEmail())
                : null);
        invoice.setBuyerPhone(order != null ? order.getCustomerPhone() : null);
        invoice.setPaymentMethod("TIEN_MAT".equalsIgnoreCase(transaction.getGateway()) ? "TM" : "CK");
        invoice.setSubtotal(transaction.getAmountIn());
        invoice.setTaxTreatment(taxTreatment);
        invoice.setTaxRate(taxTreatment.getSepayTaxRate());
        invoice.setTaxAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(transaction.getAmountIn());
        invoice.setRetryCount(0);
        invoice.setCreatedAt(OffsetDateTime.now());
        invoice.setUpdatedAt(OffsetDateTime.now());

        try {
            ElectronicInvoice saved = invoiceRepository.save(invoice);
            recordAudit(saved.getId(), "TRANSACTION_INVOICE_QUEUED", "SYSTEM",
                    "Xếp hàng lập hóa đơn theo giao dịch " + transaction.getSepayTransactionId(),
                    Map.of("payment_transaction_id", transaction.getId().toString()));
            return saved;
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            return invoiceRepository.findByPaymentTransactionId(transaction.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Không thể khởi tạo hóa đơn cho giao dịch " + transaction.getSepayTransactionId(), conflict));
        }
    }

    /**
     * Kích hoạt phát hành hóa đơn bất đồng bộ
     */
    @Async
    public void issueInvoiceAsync(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return;
        ElectronicInvoice invoice = initOrGetInvoice(order);
        executeCreateInvoice(invoice, order);
    }

    /**
     * Phương thức phát hành hóa đơn (Flow A: Tự động cho đơn đã thanh toán)
     */
    @Transactional
    public ElectronicInvoice issueInvoice(Order order) {
        ElectronicInvoice invoice = initOrGetInvoice(order);
        return executeCreateInvoice(invoice, order);
    }

    /**
     * Thực hiện gửi yêu cầu tạo hóa đơn tới SePay eInvoice
     */
    @Transactional
    public ElectronicInvoice executeCreateInvoice(ElectronicInvoice invoice, Order order) {
        if (invoice.getStatus() == InvoiceStatus.ISSUED) {
            return invoice;
        }
        if (invoice.getStatus() == InvoiceStatus.PROCESSING && invoice.getCreateTrackingCode() != null) {
            return invoice;
        }

        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);

        // 0. Safety Checks: Kill switch & Production activation gates
        if (Boolean.FALSE.equals(settings.getAutoInvoiceEnabled())) {
            log.warn("[KILL-SWITCH ACTIVE] Bỏ qua phát hành HĐĐT {}", invoice.getReferenceCode());
            recordAudit(invoice.getId(), "AUTO_INVOICE_DISABLED", "SYSTEM", "Kill switch đang kích hoạt. Bỏ qua phát hành hóa đơn.", null);
            return invoice;
        }

        if (settings.isProductionContext()) {
            ProductionActivationState state = settings.getActivationState();
            if (state == ProductionActivationState.PRODUCTION_CONFIGURED || state == ProductionActivationState.PRODUCTION_READY) {
                String errMsg = "Hệ thống đang ở trạng thái " + state + ". Nghiêm cấm phát hành hóa đơn thật lên SePay Production khi chưa kích hoạt Pilot hoặc Active.";
                log.error("[SAFETY BLOCKED] {}", errMsg);
                recordAudit(invoice.getId(), "ISSUANCE_BLOCKED_SAFETY_STATE", "SYSTEM", errMsg, null);
                throw new BusinessRuleException(errMsg);
            }
            if (state == ProductionActivationState.PRODUCTION_PILOT) {
                boolean pilotApproved = order != null && (Boolean.TRUE.equals(order.getPilotApproved())
                        || isOrderInPilotAllowlist(order.getOrderCode(), settings.getPilotOrderAllowlist()));
                if (!pilotApproved) {
                    String errMsg = "Chế độ PRODUCTION_PILOT chỉ phát hành giao dịch thuộc đơn hàng đã được Admin phê duyệt.";
                    log.error("[PILOT BLOCKED] {}", errMsg);
                    recordAudit(invoice.getId(), "ISSUANCE_BLOCKED_PILOT_NOT_APPROVED", "SYSTEM", errMsg, null);
                    throw new BusinessRuleException(errMsg);
                }
            }
        }

        // Quy trình kiểm tra trước khi gửi lại (Pre-resend check)
        SepayEInvoiceClient.InvoiceDetailResult preCheck = null;
        try {
            preCheck = sepayEInvoiceClient.getInvoiceDetail(settings, invoice.getReferenceCode());
        } catch (Exception ex) {
            log.warn("Lỗi khi kiểm tra pre-check hóa đơn {}: {}", invoice.getReferenceCode(), ex.getMessage());
        }

        if (preCheck != null && preCheck.success()) {
            log.info("Hóa đơn {} đã tồn tại trên SePay. Tiến hành đồng bộ trạng thái...", invoice.getReferenceCode());
            recordAudit(invoice.getId(), "INVOICE_ALREADY_EXISTS", "SYSTEM", "Hóa đơn đã tồn tại trên SePay trước khi gửi create", null);
            return synchronizeFromDetail(invoice, order, preCheck);
        } else if (preCheck != null && !preCheck.notFound()) {
            // Lỗi mạng/timeout không xác định
            log.warn("Không thể xác định trạng thái hóa đơn {} trên SePay: {}", invoice.getReferenceCode(), preCheck.errorMessage());
            if (invoice.getCreateTrackingCode() != null || invoice.getStatus() == InvoiceStatus.PROCESSING) {
                invoice.setStatus(InvoiceStatus.UNKNOWN);
                invoice.setErrorCategory(InvoiceErrorCategory.UNDETERMINED);
                invoice.setErrorLog("Chưa xác nhận được trạng thái với SePay: " + preCheck.errorMessage());
                invoice.setNextRetryAt(null);
                invoice.setUpdatedAt(OffsetDateTime.now());
                recordAudit(invoice.getId(), "ISSUANCE_RESULT_UNDETERMINED", "SYSTEM", "Lỗi kết nối khi kiểm tra trạng thái trước khi gửi", null);
                return invoiceRepository.save(invoice);
            }
        }

        // Chuyển sang CREATING
        if (invoice.getFirstSubmittedAt() == null) {
            invoice.setFirstSubmittedAt(OffsetDateTime.now());
        }
        invoice.setStatus(InvoiceStatus.CREATING);
        invoice.setUpdatedAt(OffsetDateTime.now());
        invoiceRepository.save(invoice);
        recordAudit(invoice.getId(), "CREATE_SENT", "SYSTEM", "Bắt đầu gửi lệnh tạo HĐĐT tới SePay", null);

        try {
            Course course = order != null ? courseRepository.findById(order.getCourseId()).orElse(null) : null;

            // Tìm Provider Account ID nếu chưa cấu hình
            String providerAccountId = settings.getActiveProviderAccountId();
            String templateCode = settings.getActiveTemplateCode() != null ? settings.getActiveTemplateCode() : "1";
            String series = settings.getActiveInvoiceSeries() != null ? settings.getActiveInvoiceSeries() : "C26TSE";

            if (providerAccountId == null || providerAccountId.isBlank()) {
                try {
                    List<SepayEInvoiceClient.ProviderAccountDto> providers = sepayEInvoiceClient.getProviderAccounts(settings);
                    if (providers != null && !providers.isEmpty()) {
                        SepayEInvoiceClient.ProviderAccountDto active = providers.stream()
                                .filter(SepayEInvoiceClient.ProviderAccountDto::active)
                                .findFirst()
                                .orElse(providers.get(0));
                        providerAccountId = active.id();
                        if (active.templates() != null && !active.templates().isEmpty()) {
                            templateCode = active.templates().get(0).templateCode();
                            series = active.templates().get(0).invoiceSeries();
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (providerAccountId == null && invoice.getProviderAccountId() != null) {
                providerAccountId = invoice.getProviderAccountId();
            }

            invoice.setProviderAccountId(providerAccountId);
            invoice.setTemplateCode(templateCode);
            invoice.setInvoiceSeries(series);

            // Xác định thuế suất theo loại hóa đơn: Sales Invoice không ép tax_rate
            Integer payloadTaxRate;
            if ("SALES".equalsIgnoreCase(settings.getInvoiceType())) {
                payloadTaxRate = null;
            } else {
                payloadTaxRate = invoice.getTaxRate() != null ? invoice.getTaxRate()
                        : (invoice.getTaxTreatment() != null ? invoice.getTaxTreatment().getSepayTaxRate() : -2);
            }

            BigDecimal invoiceAmount = invoice.getTotalAmount() != null
                    ? invoice.getTotalAmount()
                    : (order != null ? order.getAmount() : null);
            if (invoiceAmount == null || invoiceAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException("Hóa đơn không có số tiền thực nhận hợp lệ");
            }

            SepayEInvoiceClient.CreateInvoicePayload payload = new SepayEInvoiceClient.CreateInvoicePayload(
                    templateCode,
                    series,
                    providerAccountId,
                    invoice.getReferenceCode(),
                    invoice.getPaymentMethod(),
                    Boolean.TRUE.equals(invoice.getIsDraft()),
                    invoice.getBuyerType(),
                    invoice.getBuyerName(),
                    invoice.getBuyerLegalName(),
                    invoice.getBuyerTaxCode(),
                    invoice.getBuyerAddress(),
                    invoice.getBuyerEmail(),
                    invoice.getBuyerPhone(),
                    order != null ? order.getOrderCode() : invoice.getReferenceCode(),
                    course != null ? course.getCode() : "IELTS-TUITION",
                    invoice.getProductName() != null ? invoice.getProductName() : "Đóng học phí đào tạo IELTS",
                    "Khóa",
                    1,
                    invoiceAmount.longValueExact(),
                    payloadTaxRate,
                    "Đóng học phí đào tạo IELTS"
            );

            SepayEInvoiceClient.CreateResult result = sepayEInvoiceClient.createInvoice(settings, payload);

            if (result.success() && result.trackingCode() != null) {
                invoice.setCreateTrackingCode(result.trackingCode());
                invoice.setStatus(InvoiceStatus.PROCESSING);
                invoice.setNextRetryAt(OffsetDateTime.now().plusSeconds(3));
                invoice.setErrorLog(null);
                invoice.setErrorCategory(null);
                invoice.setUpdatedAt(OffsetDateTime.now());
                recordAudit(invoice.getId(), "CREATE_ACCEPTED", "SYSTEM",
                        "Đã tiếp nhận yêu cầu. Tracking Code: " + result.trackingCode(),
                        Map.of("tracking_code", result.trackingCode()));
                log.info("Gửi tạo HĐĐT thành công cho nguồn {}: tracking_code={}", invoice.getReferenceCode(), result.trackingCode());
            } else {
                String errCode = result.errorCode() != null ? result.errorCode() : "";
                // Xử lý 409 EINVOICE_DOCUMENT_EXISTED
                if ("EINVOICE_DOCUMENT_EXISTED".equalsIgnoreCase(errCode)) {
                    log.warn("SePay báo hóa đơn {} đã tồn tại (409). Tiến hành đồng bộ chi tiết...", invoice.getReferenceCode());
                    recordAudit(invoice.getId(), "INVOICE_ALREADY_EXISTS", "SYSTEM", "SePay báo hóa đơn đã tồn tại (409)", null);
                    invoice.setErrorCategory(InvoiceErrorCategory.RECONCILABLE);
                    invoice.setProviderErrorCode(errCode);
                    invoice.setProviderErrorMessage(result.errorMessage());

                    SepayEInvoiceClient.InvoiceDetailResult existDetail = null;
                    try {
                        existDetail = sepayEInvoiceClient.getInvoiceDetail(settings, invoice.getReferenceCode());
                    } catch (Exception ex) {
                        log.warn("Lỗi mạng khi lấy chi tiết sau 409 cho {}: {}", invoice.getReferenceCode(), ex.getMessage());
                    }

                    if (existDetail != null && existDetail.success()) {
                        // CASE 1 & CASE 2: Đồng bộ chi tiết (nếu issued -> ISSUED & RECONCILED; nếu draft -> DRAFT)
                        return synchronizeFromDetail(invoice, order, existDetail);
                    } else if (existDetail != null && existDetail.notFound()) {
                        // CASE 3: 409 nói document existed nhưng GET detail 404 (chưa index kịp hoặc bất thường)
                        // KHÔNG create lại, đặt ReconciliationStatus = PENDING, schedule reconciliation lại
                        invoice.setReconciliationStatus(ReconciliationStatus.PENDING);
                        invoice.setErrorCategory(InvoiceErrorCategory.RECONCILABLE);
                        invoice.setErrorLog("SePay báo 409 đã tồn tại nhưng GET detail trả về 404. Chờ đối soát lại...");
                        invoice.setNextRetryAt(OffsetDateTime.now().plusSeconds(30));
                        invoice.setUpdatedAt(OffsetDateTime.now());
                        recordAudit(invoice.getId(), "RECONCILIATION_SCHEDULED", "SYSTEM", "409 Document Existed nhưng GET detail 404, lên lịch đối soát lại", null);
                        return invoiceRepository.save(invoice);
                    } else {
                        // CASE 4: Timeout / HTTP 5xx / Lỗi mạng khi GET detail
                        // KHÔNG create lại, giữ nguyên reference_code, đặt ReconciliationStatus = PENDING, schedule reconciliation lại
                        String errMsg = existDetail != null ? existDetail.errorMessage() : "Timeout hoặc lỗi kết nối khi lấy chi tiết sau 409";
                        invoice.setReconciliationStatus(ReconciliationStatus.PENDING);
                        invoice.setErrorCategory(InvoiceErrorCategory.RECONCILABLE);
                        invoice.setErrorLog("SePay báo 409 đã tồn tại nhưng gặp lỗi khi lấy chi tiết: " + errMsg);
                        invoice.setNextRetryAt(OffsetDateTime.now().plusSeconds(30));
                        invoice.setUpdatedAt(OffsetDateTime.now());
                        recordAudit(invoice.getId(), "RECONCILIATION_SCHEDULED", "SYSTEM", "409 Document Existed, lên lịch đối soát lại sau lỗi mạng", null);
                        return invoiceRepository.save(invoice);
                    }
                }

                InvoiceErrorCategory cat = determineErrorCategory(errCode, result.errorMessage());
                invoice.setStatus(InvoiceStatus.FAILED);
                invoice.setErrorCategory(cat);
                invoice.setProviderErrorCode(errCode);
                invoice.setProviderErrorMessage(result.errorMessage());
                invoice.setErrorLog(result.errorMessage());
                invoice.setNextRetryAt(cat == InvoiceErrorCategory.REQUIRES_ACTION ? null : OffsetDateTime.now().plusSeconds(30));
                invoice.setUpdatedAt(OffsetDateTime.now());
                recordAudit(invoice.getId(), "CREATE_FAILED", "SYSTEM",
                        "SePay từ chối: " + result.errorMessage(),
                        Map.of("error_code", errCode, "error_message", result.errorMessage() != null ? result.errorMessage() : ""));
                log.warn("SePay từ chối tạo HĐĐT {}: {} (code: {})", invoice.getReferenceCode(), result.errorMessage(), errCode);
            }

            return invoiceRepository.save(invoice);

        } catch (Exception ex) {
            log.error("Ngoại lệ khi tạo HĐĐT {}: {}", invoice.getReferenceCode(), ex.getMessage(), ex);
            invoice.setStatus(InvoiceStatus.FAILED);
            invoice.setErrorCategory(InvoiceErrorCategory.RETRYABLE);
            invoice.setRetryCount(invoice.getRetryCount() + 1);
            invoice.setErrorLog(ex.getMessage());
            invoice.setUpdatedAt(OffsetDateTime.now());
            recordAudit(invoice.getId(), "CREATE_FAILED", "SYSTEM", ex.getMessage(), null);
            return invoiceRepository.save(invoice);
        }
    }

    /**
     * Flow B: Kế toán bấm [Phát hành] một hóa đơn DRAFT đã duyệt
     */
    @Transactional
    public ElectronicInvoice issueDraftInvoice(UUID invoiceId) {
        ElectronicInvoice invoice = invoiceRepository.findById(invoiceId)
                .or(() -> invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(invoiceId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.ISSUED) {
            return invoice;
        }

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException("Chỉ có thể phát hành hóa đơn đang ở trạng thái Nháp (DRAFT). Trạng thái hiện tại: " + invoice.getStatus());
        }

        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);
        Order order = invoice.getOrderId() != null
                ? orderRepository.findById(invoice.getOrderId()).orElse(null)
                : null;

        if (settings.isProductionContext()) {
            ProductionActivationState state = settings.getActivationState();
            if (state == ProductionActivationState.PRODUCTION_CONFIGURED || state == ProductionActivationState.PRODUCTION_READY) {
                String errMsg = "Hệ thống đang ở trạng thái " + state + ". Nghiêm cấm phát hành hóa đơn thật lên SePay Production.";
                log.error("[SAFETY BLOCKED] {}", errMsg);
                throw new BusinessRuleException(errMsg);
            }
            if (state == ProductionActivationState.PRODUCTION_PILOT) {
                boolean pilotApproved = order != null && (Boolean.TRUE.equals(order.getPilotApproved()) || isOrderInPilotAllowlist(order.getOrderCode(), settings.getPilotOrderAllowlist()));
                if (!pilotApproved) {
                    String errMsg = "Chế độ Thử nghiệm có kiểm soát (PRODUCTION_PILOT): Chỉ đơn hàng được Admin phê duyệt mới được phát hành hóa đơn thật.";
                    log.error("[PILOT BLOCKED] {}", errMsg);
                    throw new BusinessRuleException(errMsg);
                }
            }
        }

        // Pre-check trước khi phát hành nháp
        SepayEInvoiceClient.InvoiceDetailResult preCheck = null;
        try {
            preCheck = sepayEInvoiceClient.getInvoiceDetail(settings, invoice.getReferenceCode());
        } catch (Exception ignored) {}
        if (preCheck != null && preCheck.success() && ("issued".equalsIgnoreCase(preCheck.status()) || "signed".equalsIgnoreCase(preCheck.status()))) {
            log.info("Hóa đơn nháp {} đã được phát hành trên SePay. Tiến hành đồng bộ...", invoice.getReferenceCode());
            return synchronizeFromDetail(invoice, order, preCheck);
        }

        invoice.setStatus(InvoiceStatus.ISSUING);
        invoice.setUpdatedAt(OffsetDateTime.now());
        invoiceRepository.save(invoice);
        recordAudit(invoice.getId(), "ISSUE_SENT", "ADMIN", "Quản trị viên bấm phát hành hóa đơn nháp", null);

        try {
            SepayEInvoiceClient.IssueDraftResult result = sepayEInvoiceClient.issueDraft(settings, invoice.getReferenceCode());
            if (result.success() && result.trackingCode() != null) {
                invoice.setIssueTrackingCode(result.trackingCode());
                invoice.setNextRetryAt(OffsetDateTime.now().plusSeconds(3));
                invoice.setErrorLog(null);
                invoice.setErrorCategory(null);
                invoice.setUpdatedAt(OffsetDateTime.now());
                recordAudit(invoice.getId(), "ISSUE_ACCEPTED", "ADMIN", "Lệnh phát hành được SePay tiếp nhận. Tracking: " + result.trackingCode(), Map.of("tracking_code", result.trackingCode()));
            } else {
                String errCode = result.errorCode() != null ? result.errorCode() : "";
                if ("EINVOICE_DOCUMENT_EXISTED".equalsIgnoreCase(errCode)) {
                    SepayEInvoiceClient.InvoiceDetailResult existDetail = sepayEInvoiceClient.getInvoiceDetail(settings, invoice.getReferenceCode());
                    if (existDetail.success()) {
                        return synchronizeFromDetail(invoice, order, existDetail);
                    }
                }
                InvoiceErrorCategory cat = determineErrorCategory(errCode, result.errorMessage());
                invoice.setStatus(InvoiceStatus.FAILED);
                invoice.setErrorCategory(cat);
                invoice.setErrorLog(result.errorMessage());
                invoice.setProviderErrorCode(errCode);
                invoice.setProviderErrorMessage(result.errorMessage());
                invoice.setNextRetryAt(cat == InvoiceErrorCategory.REQUIRES_ACTION ? null : OffsetDateTime.now().plusSeconds(30));
                invoice.setUpdatedAt(OffsetDateTime.now());
                recordAudit(invoice.getId(), "ISSUE_FAILED", "ADMIN", "Lỗi phát hành nháp: " + result.errorMessage(), null);
            }
            return invoiceRepository.save(invoice);
        } catch (Exception ex) {
            log.error("Lỗi khi phát hành hóa đơn nháp {}: {}", invoiceId, ex.getMessage());
            invoice.setStatus(InvoiceStatus.FAILED);
            invoice.setErrorCategory(InvoiceErrorCategory.RETRYABLE);
            invoice.setErrorLog(ex.getMessage());
            invoice.setUpdatedAt(OffsetDateTime.now());
            recordAudit(invoice.getId(), "ISSUE_FAILED", "ADMIN", ex.getMessage(), null);
            return invoiceRepository.save(invoice);
        }
    }

    /**
     * Polling kiểm tra và thúc đẩy trạng thái hóa đơn (dùng bởi ElectronicInvoiceWorker)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndAdvanceInvoice(UUID invoiceId) {
        ElectronicInvoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) return;

        invoice.setLastStatusCheckedAt(OffsetDateTime.now());
        Order order = invoice.getOrderId() != null
                ? orderRepository.findById(invoice.getOrderId()).orElse(null)
                : null;
        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);

        // Trường hợp đang chờ đối soát chi tiết
        if (invoice.getReconciliationStatus() == ReconciliationStatus.PENDING) {
            reconcileInvoiceDetail(invoice, order, settings);
            invoiceRepository.save(invoice);
            return;
        }

        // 1. Kiểm tra trạng thái tạo hóa đơn (PROCESSING)
        if (invoice.getStatus() == InvoiceStatus.PROCESSING && invoice.getCreateTrackingCode() != null) {
            SepayEInvoiceClient.CheckStatusResult checkResult = sepayEInvoiceClient.checkCreateStatus(settings, invoice.getCreateTrackingCode());

            if (checkResult.isUndetermined()) {
                invoice.setStatus(InvoiceStatus.UNKNOWN);
                invoice.setReconciliationStatus(ReconciliationStatus.REQUIRES_REVIEW);
                invoice.setErrorCategory(InvoiceErrorCategory.UNDETERMINED);
                invoice.setErrorLog(checkResult.errorMessage());
                invoice.setNextRetryAt(null);
                invoice.setUpdatedAt(OffsetDateTime.now());
                recordAudit(invoice.getId(), "ISSUANCE_RESULT_UNDETERMINED", "SYSTEM", checkResult.errorMessage(), null);
                invoiceRepository.save(invoice);
                return;
            }

            if (checkResult.completed()) {
                if (checkResult.success()) {
                    if (checkResult.isDraft() || Boolean.TRUE.equals(invoice.getIsDraft())) {
                        invoice.setStatus(InvoiceStatus.DRAFT);
                        invoice.setReconciliationStatus(ReconciliationStatus.RECONCILED);
                        invoice.setInvoiceNumber(checkResult.invoiceNumber());
                        invoice.setInvoiceSeries(checkResult.invoiceSeries());
                        invoice.setPdfUrl(checkResult.pdfUrl());
                        invoice.setXmlUrl(checkResult.xmlUrl());
                        invoice.setUpdatedAt(OffsetDateTime.now());
                        recordAudit(invoice.getId(), "DRAFT_READY", "SYSTEM", "Hóa đơn nháp đã tạo thành công, chờ kế toán duyệt", null);
                    } else {
                        if (checkResult.invoiceNumber() != null) {
                            invoice.setInvoiceNumber(checkResult.invoiceNumber());
                            if (checkResult.invoiceSeries() != null) invoice.setInvoiceSeries(checkResult.invoiceSeries());
                            if (checkResult.cqtCode() != null) invoice.setCqtCode(checkResult.cqtCode());
                            if (checkResult.lookupCode() != null) invoice.setLookupCode(checkResult.lookupCode());
                            if (checkResult.pdfUrl() != null) invoice.setPdfUrl(checkResult.pdfUrl());
                            if (checkResult.xmlUrl() != null) invoice.setXmlUrl(checkResult.xmlUrl());
                            invoice.setStatus(InvoiceStatus.ISSUED);
                            invoice.setReconciliationStatus(ReconciliationStatus.RECONCILED);
                            invoice.setIssuedAt(OffsetDateTime.now());
                            invoice.setErrorLog(null);
                            invoice.setErrorCategory(null);
                            invoice.setNextRetryAt(null);
                            invoice.setUpdatedAt(OffsetDateTime.now());
                            recordAudit(invoice.getId(), "INVOICE_ISSUED", "SYSTEM",
                                    String.format("Phát hành thành công: Số HĐ=%s, Ký hiệu=%s, CQT=%s",
                                            invoice.getInvoiceNumber(), invoice.getInvoiceSeries(), invoice.getCqtCode()),
                                    Map.of("invoice_number", invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : ""));
                            if (order != null) {
                                try {
                                    emailService.sendInvoiceIssuedEmail(order, invoice);
                                    recordAudit(invoice.getId(), "EMAIL_SENT", "SYSTEM", "Đã gửi email hóa đơn điện tử cho học viên", null);
                                } catch (Exception ex) {
                                    log.warn("Không thể gửi email thông báo hóa đơn: {}", ex.getMessage());
                                }
                            }
                        } else {
                            reconcileInvoiceDetail(invoice, order, settings);
                        }
                    }
                } else {
                    invoice.setStatus(InvoiceStatus.FAILED);
                    invoice.setErrorLog(checkResult.errorMessage());
                    invoice.setProviderErrorCode(checkResult.errorCode());
                    invoice.setProviderErrorMessage(checkResult.errorMessage());
                    invoice.setErrorCategory(determineErrorCategory(checkResult.errorCode(), checkResult.errorMessage()));
                    invoice.setUpdatedAt(OffsetDateTime.now());
                    recordAudit(invoice.getId(), "CREATE_FAILED", "SYSTEM", checkResult.errorMessage(), null);
                }
            } else {
                handlePendingPolling(invoice, checkResult, settings);
            }
            invoiceRepository.save(invoice);
            return;
        }

        // 2. Kiểm tra trạng thái phát hành hóa đơn nháp (ISSUING)
        if (invoice.getStatus() == InvoiceStatus.ISSUING && invoice.getIssueTrackingCode() != null) {
            SepayEInvoiceClient.CheckStatusResult checkResult = sepayEInvoiceClient.checkIssueStatus(settings, invoice.getIssueTrackingCode());

            if (checkResult.isUndetermined()) {
                invoice.setStatus(InvoiceStatus.UNKNOWN);
                invoice.setReconciliationStatus(ReconciliationStatus.REQUIRES_REVIEW);
                invoice.setErrorCategory(InvoiceErrorCategory.UNDETERMINED);
                invoice.setErrorLog(checkResult.errorMessage());
                invoice.setNextRetryAt(null);
                invoice.setUpdatedAt(OffsetDateTime.now());
                recordAudit(invoice.getId(), "ISSUANCE_RESULT_UNDETERMINED", "SYSTEM", checkResult.errorMessage(), null);
                invoiceRepository.save(invoice);
                return;
            }

            if (checkResult.completed()) {
                if (checkResult.success()) {
                    if (checkResult.invoiceNumber() != null) {
                        invoice.setInvoiceNumber(checkResult.invoiceNumber());
                        if (checkResult.invoiceSeries() != null) invoice.setInvoiceSeries(checkResult.invoiceSeries());
                        if (checkResult.cqtCode() != null) invoice.setCqtCode(checkResult.cqtCode());
                        if (checkResult.lookupCode() != null) invoice.setLookupCode(checkResult.lookupCode());
                        if (checkResult.pdfUrl() != null) invoice.setPdfUrl(checkResult.pdfUrl());
                        if (checkResult.xmlUrl() != null) invoice.setXmlUrl(checkResult.xmlUrl());
                        invoice.setStatus(InvoiceStatus.ISSUED);
                        invoice.setReconciliationStatus(ReconciliationStatus.RECONCILED);
                        invoice.setIssuedAt(OffsetDateTime.now());
                        invoice.setErrorLog(null);
                        invoice.setErrorCategory(null);
                        invoice.setNextRetryAt(null);
                        invoice.setUpdatedAt(OffsetDateTime.now());
                        recordAudit(invoice.getId(), "INVOICE_ISSUED", "SYSTEM",
                                String.format("Phát hành thành công: Số HĐ=%s, Ký hiệu=%s, CQT=%s",
                                        invoice.getInvoiceNumber(), invoice.getInvoiceSeries(), invoice.getCqtCode()),
                                Map.of("invoice_number", invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : ""));
                        if (order != null) {
                            try {
                                emailService.sendInvoiceIssuedEmail(order, invoice);
                                recordAudit(invoice.getId(), "EMAIL_SENT", "SYSTEM", "Đã gửi email hóa đơn điện tử cho học viên", null);
                            } catch (Exception ex) {
                                log.warn("Không thể gửi email thông báo hóa đơn: {}", ex.getMessage());
                            }
                        }
                    } else {
                        reconcileInvoiceDetail(invoice, order, settings);
                    }
                } else {
                    invoice.setStatus(InvoiceStatus.FAILED);
                    invoice.setErrorLog(checkResult.errorMessage());
                    invoice.setProviderErrorCode(checkResult.errorCode());
                    invoice.setProviderErrorMessage(checkResult.errorMessage());
                    invoice.setErrorCategory(determineErrorCategory(checkResult.errorCode(), checkResult.errorMessage()));
                    invoice.setUpdatedAt(OffsetDateTime.now());
                    recordAudit(invoice.getId(), "ISSUE_FAILED", "SYSTEM", checkResult.errorMessage(), null);
                }
            } else {
                handlePendingPolling(invoice, checkResult, settings);
            }
            invoiceRepository.save(invoice);
        }
    }

    /**
     * Xử lý chu kỳ polling cho trạng thái Pending theo hợp đồng SePay (không timeout tùy tiện sau 2 giờ)
     */
    private void handlePendingPolling(ElectronicInvoice invoice, SepayEInvoiceClient.CheckStatusResult checkResult, BillingSetting settings) {
        int retries = invoice.getRetryCount() + 1;
        invoice.setRetryCount(retries);
        invoice.setReconciliationStatus(ReconciliationStatus.PENDING);

        if (checkResult.nextRetryAt() != null) {
            invoice.setNextRetryAt(checkResult.nextRetryAt());
            log.info("Hóa đơn {} đang Pending, SePay hẹn thử lại lúc {}", invoice.getId(), checkResult.nextRetryAt());
            recordAudit(invoice.getId(), "RETRY_SCHEDULED", "SYSTEM", "SePay hẹn thử lại: " + checkResult.nextRetryAt(), null);
        } else {
            OffsetDateTime submitted = invoice.getFirstSubmittedAt() != null ? invoice.getFirstSubmittedAt() : invoice.getCreatedAt();
            long minutesSinceSubmitted = java.time.Duration.between(submitted, OffsetDateTime.now()).toMinutes();

            if (minutesSinceSubmitted >= 120) {
                // Quá 2 giờ: Tiếp tục polling với nhịp thưa (3 phút) và tra cứu chi tiết đối soát
                log.info("Hóa đơn {} đã Pending quá 2 giờ ({} phút). Tiếp tục nhịp poll chậm và đối soát detail...", invoice.getId(), minutesSinceSubmitted);
                invoice.setNextRetryAt(OffsetDateTime.now().plusSeconds(180));
                try {
                    SepayEInvoiceClient.InvoiceDetailResult detail = sepayEInvoiceClient.getInvoiceDetail(settings, invoice.getReferenceCode());
                    if (detail != null && detail.success() && ("issued".equalsIgnoreCase(detail.status()) || "signed".equalsIgnoreCase(detail.status()) || detail.invoiceNumber() != null)) {
                        Order order = invoice.getOrderId() != null
                                ? orderRepository.findById(invoice.getOrderId()).orElse(null)
                                : null;
                        synchronizeFromDetail(invoice, order, detail);
                        return;
                    }
                } catch (Exception ignored) {}
            } else {
                // Trong vòng 2 giờ: poll giãn cách từ 30s đến tối đa 90s
                int delaySeconds = Math.min(90, Math.max(30, 5 * Math.min(retries, 18)));
                invoice.setNextRetryAt(OffsetDateTime.now().plusSeconds(delaySeconds));
            }
            recordAudit(invoice.getId(), "CREATE_PENDING", "SYSTEM", "Hóa đơn đang chờ SePay xử lý (lần " + retries + ")", null);
        }
        invoice.setUpdatedAt(OffsetDateTime.now());
    }

    /**
     * Đối soát thông tin chi tiết hóa đơn từ SePay và đánh dấu ISSUED
     */
    public void reconcileInvoiceDetail(ElectronicInvoice invoice, Order order, BillingSetting settings) {
        SepayEInvoiceClient.InvoiceDetailResult detail = null;
        try {
            detail = sepayEInvoiceClient.getInvoiceDetail(settings, invoice.getReferenceCode());
        } catch (Exception ex) {
            log.warn("Lỗi khi lấy chi tiết hóa đơn {}: {}", invoice.getReferenceCode(), ex.getMessage());
        }
        if (detail != null && detail.success()) {
            synchronizeFromDetail(invoice, order, detail);
        } else {
            String errMsg = detail != null ? detail.errorMessage() : "Không thể lấy chi tiết hóa đơn từ SePay";
            log.warn("Chưa thể đối soát chi tiết cho hóa đơn {}: {}", invoice.getReferenceCode(), errMsg);
            invoice.setReconciliationStatus(ReconciliationStatus.PENDING);
            invoice.setNextRetryAt(OffsetDateTime.now().plusSeconds(30));
            recordAudit(invoice.getId(), "RECONCILIATION_PENDING", "SYSTEM", "Chờ đối soát chi tiết: " + errMsg, null);
        }
    }

    private ElectronicInvoice synchronizeFromDetail(ElectronicInvoice invoice, Order order, SepayEInvoiceClient.InvoiceDetailResult detail) {
        String templateCode = detail.templateCode() != null ? detail.templateCode() : (invoice.getTemplateCode() != null ? invoice.getTemplateCode() : "1");
        String series = detail.invoiceSeries() != null ? detail.invoiceSeries() : invoice.getInvoiceSeries();

        invoice.setInvoiceTemplate(series != null ? (templateCode + " - " + series) : templateCode);
        invoice.setTemplateCode(templateCode);
        invoice.setInvoiceSeries(series);
        if (detail.invoiceNumber() != null) invoice.setInvoiceNumber(detail.invoiceNumber());
        if (detail.cqtCode() != null) invoice.setCqtCode(detail.cqtCode());
        if (detail.lookupCode() != null) invoice.setLookupCode(detail.lookupCode());
        if (detail.pdfUrl() != null) invoice.setPdfUrl(detail.pdfUrl());
        if (detail.xmlUrl() != null) invoice.setXmlUrl(detail.xmlUrl());

        if ("draft".equalsIgnoreCase(detail.status())) {
            invoice.setStatus(InvoiceStatus.DRAFT);
            invoice.setReconciliationStatus(ReconciliationStatus.RECONCILED);
            invoice.setErrorLog(null);
            invoice.setErrorCategory(null);
            invoice.setNextRetryAt(null);
            invoice.setUpdatedAt(OffsetDateTime.now());
            recordAudit(invoice.getId(), "DRAFT_READY", "SYSTEM", "Hóa đơn nháp đã đồng bộ", null);
        } else if ("issued".equalsIgnoreCase(detail.status()) || "signed".equalsIgnoreCase(detail.status()) || (detail.success() && detail.invoiceNumber() != null)) {
            invoice.setStatus(InvoiceStatus.ISSUED);
            invoice.setReconciliationStatus(ReconciliationStatus.RECONCILED);
            invoice.setIssuedAt(OffsetDateTime.now());
            invoice.setErrorLog(null);
            invoice.setErrorCategory(null);
            invoice.setNextRetryAt(null);
            invoice.setUpdatedAt(OffsetDateTime.now());

            recordAudit(invoice.getId(), "INVOICE_RECONCILED", "SYSTEM",
                    String.format("Đối soát thành công: Số HĐ=%s, Ký hiệu=%s, CQT=%s",
                            invoice.getInvoiceNumber(), series, invoice.getCqtCode()),
                    Map.of("invoice_number", invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "", "pdf_url", invoice.getPdfUrl() != null ? invoice.getPdfUrl() : ""));

            log.info("Phát hành HĐĐT thành công qua SePay: Hóa đơn ID={}, Số HĐ={}, Ký hiệu={}, CQT={}",
                    invoice.getId(), invoice.getInvoiceNumber(), series, invoice.getCqtCode());

            if (order != null) {
                try {
                    emailService.sendInvoiceIssuedEmail(order, invoice);
                    recordAudit(invoice.getId(), "EMAIL_SENT", "SYSTEM", "Đã gửi email hóa đơn điện tử cho học viên", null);
                } catch (Exception ex) {
                    log.warn("Không thể gửi email thông báo hóa đơn: {}", ex.getMessage());
                    recordAudit(invoice.getId(), "EMAIL_FAILED", "SYSTEM", "Gửi email thất bại: " + ex.getMessage(), null);
                }
            }
        }

        return invoiceRepository.save(invoice);
    }

    public InvoiceErrorCategory determineErrorCategory(String errorCode, String errorMessage) {
        if (errorCode != null && !errorCode.isBlank()) {
            return switch (errorCode.toUpperCase()) {
                case "EINVOICE_DOCUMENT_EXISTED" -> InvoiceErrorCategory.RECONCILABLE;
                case "QUOTA_HAS_BEEN_USERD_UP", "REGISTRATION_NOT_TAX_APPROVED", "REGISTRATION_NOT_COMPLETE", "BILLING_IS_UNPAID", "VALIDATION_ERROR" -> InvoiceErrorCategory.REQUIRES_ACTION;
                case "UNDETERMINED" -> InvoiceErrorCategory.UNDETERMINED;
                case "INVALID_CONFIG", "PERMISSION_DENIED" -> InvoiceErrorCategory.NON_RETRYABLE;
                default -> InvoiceErrorCategory.RETRYABLE;
            };
        }
        if (errorMessage != null) {
            String lower = errorMessage.toLowerCase();
            if (lower.contains("đã tồn tại") || lower.contains("existed") || lower.contains("document_existed")) {
                return InvoiceErrorCategory.RECONCILABLE;
            }
            if (lower.contains("chưa xác nhận được kết quả") || lower.contains("undetermined")) {
                return InvoiceErrorCategory.UNDETERMINED;
            }
            if (lower.contains("quota") || lower.contains("hết hạn mức") || lower.contains("chưa đăng ký thuế") || lower.contains("chưa hoàn tất") || lower.contains("unpaid")) {
                return InvoiceErrorCategory.REQUIRES_ACTION;
            }
        }
        return InvoiceErrorCategory.RETRYABLE;
    }

    public InvoiceErrorCategory determineErrorCategory(String errorCode) {
        return determineErrorCategory(errorCode, null);
    }

    @Transactional
    public InvoiceAdminDto retryInvoice(UUID targetId) {
        ElectronicInvoice invoice = invoiceRepository.findById(targetId)
                .or(() -> invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(targetId))
                .orElse(null);

        // Kiểm tra nếu lỗi là REQUIRES_ACTION thì chặn ngay
        if (invoice != null && invoice.getErrorCategory() == InvoiceErrorCategory.REQUIRES_ACTION) {
            String errCode = invoice.getProviderErrorCode() != null ? invoice.getProviderErrorCode() : "";
            if ("QUOTA_HAS_BEEN_USERD_UP".equalsIgnoreCase(errCode)) {
                BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);
                Integer remainingQuota = sepayEInvoiceClient.getRemainingQuota(settings);
                if (remainingQuota != null && remainingQuota <= 0) {
                    throw new BusinessRuleException("Hạn ngạch hóa đơn đã hết (0 số). Vui lòng nạp thêm gói hóa đơn trên SePay trước khi thử lại.");
                }
            } else {
                throw new BusinessRuleException("Hóa đơn bị lỗi '" + invoice.getErrorLog() + "'. Vui lòng xử lý nguyên nhân trước khi thử lại.");
            }
        }

        Order order;
        if (invoice != null) {
            order = invoice.getOrderId() != null
                    ? orderRepository.findById(invoice.getOrderId()).orElse(null)
                    : null;
        } else {
            order = orderRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn hoặc đơn hàng với ID: " + targetId));
            invoice = initOrGetInvoice(order);
        }

        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);

        // Nếu lỗi là RECONCILABLE: KHÔNG gửi POST create lại, chỉ thực hiện đối soát GET detail
        if (invoice.getErrorCategory() == InvoiceErrorCategory.RECONCILABLE) {
            SepayEInvoiceClient.InvoiceDetailResult detail = null;
            try {
                detail = sepayEInvoiceClient.getInvoiceDetail(settings, invoice.getReferenceCode());
            } catch (Exception ignored) {}
            if (detail != null && detail.success()) {
                ElectronicInvoice synced = synchronizeFromDetail(invoice, order, detail);
                return toAdminDto(synced, order);
            } else {
                invoice.setReconciliationStatus(ReconciliationStatus.PENDING);
                invoice.setNextRetryAt(OffsetDateTime.now().plusSeconds(30));
                invoice.setUpdatedAt(OffsetDateTime.now());
                invoiceRepository.save(invoice);
                return toAdminDto(invoice, order);
            }
        }

        // Pre-check trước khi retry: nếu đã có trên SePay -> đồng bộ
        SepayEInvoiceClient.InvoiceDetailResult detail = null;
        try {
            detail = sepayEInvoiceClient.getInvoiceDetail(settings, invoice.getReferenceCode());
        } catch (Exception ignored) {}
        if (detail != null && detail.success()) {
            ElectronicInvoice synced = synchronizeFromDetail(invoice, order, detail);
            return toAdminDto(synced, order);
        }

        // Reset retry count và thử lại
        invoice.setRetryCount(0);
        invoice.setErrorLog(null);
        invoice.setErrorCategory(null);
        invoice.setStatus(InvoiceStatus.PENDING_ISSUE);
        invoice.setNextRetryAt(OffsetDateTime.now());
        invoiceRepository.save(invoice);
        recordAudit(invoice.getId(), "MANUAL_RETRY", "ADMIN", "Kế toán yêu cầu thử lại phát hành", null);

        ElectronicInvoice result = executeCreateInvoice(invoice, order);
        return toAdminDto(result, order);
    }

    @Transactional
    public InvoiceAdminDto recheckInvoiceStatus(UUID targetId) {
        ElectronicInvoice invoice = invoiceRepository.findById(targetId)
                .or(() -> invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(targetId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn: " + targetId));

        Order order = invoice.getOrderId() != null
                ? orderRepository.findById(invoice.getOrderId()).orElse(null)
                : null;
        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);

        recordAudit(invoice.getId(), "MANUAL_STATUS_CHECK", "ADMIN", "Kế toán kiểm tra lại trạng thái với SePay", null);

        SepayEInvoiceClient.InvoiceDetailResult detail = sepayEInvoiceClient.getInvoiceDetail(settings, invoice.getReferenceCode());
        if (detail.success()) {
            ElectronicInvoice synced = synchronizeFromDetail(invoice, order, detail);
            return toAdminDto(synced, order);
        } else if (detail.notFound()) {
            invoice.setErrorLog("SePay xác nhận chưa ghi nhận hóa đơn với reference_code: " + invoice.getReferenceCode());
            invoice.setErrorCategory(InvoiceErrorCategory.RETRYABLE);
            invoice.setUpdatedAt(OffsetDateTime.now());
            invoiceRepository.save(invoice);
        } else {
            invoice.setErrorLog("Kiểm tra trạng thái thất bại: " + detail.errorMessage());
            invoice.setUpdatedAt(OffsetDateTime.now());
            invoiceRepository.save(invoice);
        }

        return toAdminDto(invoice, order);
    }

    @Transactional
    public InvoiceAdminDto cancelInvoice(UUID targetId, String reason) {
        ElectronicInvoice invoice = invoiceRepository.findById(targetId)
                .or(() -> invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(targetId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn: " + targetId));

        Order order = invoice.getOrderId() != null
                ? orderRepository.findById(invoice.getOrderId()).orElse(null)
                : null;

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setErrorLog("Đã hủy bởi quản trị viên: " + (reason != null ? reason : "Không có lý do"));
        invoice.setUpdatedAt(OffsetDateTime.now());
        invoiceRepository.save(invoice);

        recordAudit(invoice.getId(), "CANCEL_INVOICE", "ADMIN", "Hủy hóa đơn: " + reason, null);

        return toAdminDto(invoice, order);
    }

    private void recordAudit(UUID invoiceId, String event, String actor, String message, Map<String, Object> payload) {
        try {
            InvoiceAuditLog logEntry = new InvoiceAuditLog();
            logEntry.setInvoiceId(invoiceId);
            logEntry.setEvent(event);
            logEntry.setActor(actor != null ? actor : "SYSTEM");
            logEntry.setMessage(message);
            logEntry.setPayload(payload != null ? payload : Collections.emptyMap());
            logEntry.setCreatedAt(OffsetDateTime.now());
            auditLogRepository.save(logEntry);
        } catch (Exception ex) {
            log.warn("Không thể lưu InvoiceAuditLog cho hóa đơn {}: {}", invoiceId, ex.getMessage());
        }
    }

    private Specification<ElectronicInvoice> buildSpecification(
            String query,
            InvoiceStatus status,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return (root, q, cb) -> {
            Join<ElectronicInvoice, Order> orderJoin = root.join("order", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (fromDate != null) {
                OffsetDateTime startOfDay = fromDate.atStartOfDay().atOffset(ZoneOffset.ofHours(7));
                Expression<OffsetDateTime> dateExpr = cb.coalesce(root.get("issuedAt"), root.get("createdAt"));
                predicates.add(cb.greaterThanOrEqualTo(dateExpr, startOfDay));
            }

            if (toDate != null) {
                OffsetDateTime endOfDay = toDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.ofHours(7));
                Expression<OffsetDateTime> dateExpr = cb.coalesce(root.get("issuedAt"), root.get("createdAt"));
                predicates.add(cb.lessThan(dateExpr, endOfDay));
            }

            if (query != null && !query.trim().isEmpty()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("referenceCode"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("invoiceNumber"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("cqtCode"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("lookupCode"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(orderJoin.get("orderCode"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(orderJoin.get("customerName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(orderJoin.get("customerEmail"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(orderJoin.get("invoiceTaxCode"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(orderJoin.get("invoiceCompanyName"), "")), pattern)
                ));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public Page<InvoiceAdminDto> searchInvoices(
            String query,
            InvoiceStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        Specification<ElectronicInvoice> spec = buildSpecification(query, status, fromDate, toDate);

        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        return invoiceRepository.findAll(spec, sortedPageable)
                .map(inv -> {
                    Order order = inv.getOrder() != null
                            ? inv.getOrder()
                            : (inv.getOrderId() != null ? orderRepository.findById(inv.getOrderId()).orElse(null) : null);
                    return toAdminDto(inv, order);
                });
    }

    public InvoiceStatsDto getInvoiceStats(LocalDate fromDate, LocalDate toDate) {
        Specification<ElectronicInvoice> spec = buildSpecification(null, null, fromDate, toDate);
        List<ElectronicInvoice> list = invoiceRepository.findAll(spec);

        long total = list.size();
        long issued = 0;
        long pending = 0;
        long failed = 0;
        long cancelled = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (ElectronicInvoice inv : list) {
            InvoiceStatus s = inv.getStatus();
            if (s == InvoiceStatus.ISSUED) {
                issued++;
                if (inv.getTotalAmount() != null) {
                    totalAmount = totalAmount.add(inv.getTotalAmount());
                }
            } else if (s == InvoiceStatus.PENDING_ISSUE || s == InvoiceStatus.CREATING || s == InvoiceStatus.PROCESSING || s == InvoiceStatus.ISSUING || s == InvoiceStatus.DRAFT) {
                pending++;
            } else if (s == InvoiceStatus.FAILED || s == InvoiceStatus.UNKNOWN) {
                failed++;
            } else if (s == InvoiceStatus.CANCELLED) {
                cancelled++;
            }
        }

        return new InvoiceStatsDto(total, issued, pending, failed, cancelled, totalAmount);
    }

    public List<InvoiceAdminDto> exportInvoices(
            String query,
            InvoiceStatus status,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        Specification<ElectronicInvoice> spec = buildSpecification(query, status, fromDate, toDate);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        return invoiceRepository.findAll(spec, sort).stream()
                .limit(2000)
                .map(inv -> {
                    Order ord = inv.getOrder() != null
                            ? inv.getOrder()
                            : (inv.getOrderId() != null ? orderRepository.findById(inv.getOrderId()).orElse(null) : null);
                    return toAdminDto(inv, ord);
                })
                .toList();
    }

    public InvoiceAdminDto getInvoiceAdmin(UUID id) {
        ElectronicInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn"));
        Order order = invoice.getOrderId() != null
                ? orderRepository.findById(invoice.getOrderId()).orElse(null)
                : null;
        return toAdminDto(invoice, order);
    }

    public InvoiceAdminDto toAdminDto(ElectronicInvoice invoice, Order order) {
        return new InvoiceAdminDto(
                invoice.getId(),
                invoice.getOrderId(),
                invoice.getPaymentTransactionId(),
                order != null ? order.getOrderCode() : "QR-TINH",
                invoice.getReferenceCode(),
                invoice.getProductName(),
                invoice.getBuyerName() != null ? invoice.getBuyerName() : (order != null ? order.getCustomerName() : "Người nộp học phí"),
                order != null ? order.getCustomerEmail() : invoice.getBuyerEmail(),
                invoice.getTotalAmount(),
                order != null ? order.getBuyerType() : InvoiceBuyerType.PERSONAL,
                order != null ? order.getInvoiceCompanyName() : null,
                order != null ? order.getInvoiceTaxCode() : null,
                invoice.getInvoiceTemplate(),
                invoice.getInvoiceSeries(),
                invoice.getInvoiceNumber(),
                invoice.getCqtCode(),
                invoice.getLookupCode(),
                invoice.getLookupUrl(),
                invoice.getPdfUrl(),
                invoice.getXmlUrl(),
                invoice.getStatus(),
                invoice.getIsDraft(),
                invoice.getRetryCount(),
                invoice.getCreateTrackingCode(),
                invoice.getIssueTrackingCode(),
                invoice.getProvider(),
                invoice.getErrorLog(),
                invoice.getIssuedAt(),
                invoice.getCreatedAt(),
                invoice.getNextRetryAt(),
                invoice.getReconciliationStatus(),
                invoice.getTaxTreatment(),
                invoice.getErrorCategory()
        );
    }

    @Transactional
    public void logKillSwitchSkipped(ElectronicInvoice invoice) {
        if (invoice != null && invoice.getId() != null) {
            recordAudit(invoice.getId(), "AUTO_INVOICE_DISABLED", "SYSTEM", "Kill switch đang kích hoạt (auto_invoice_enabled=false). Tự động phát hành hóa đơn bị bỏ qua.", null);
        }
    }

    public boolean isOrderInPilotAllowlist(String orderCode, String allowlistJson) {
        if (orderCode == null || allowlistJson == null || allowlistJson.isBlank() || "[]".equals(allowlistJson.trim())) {
            return false;
        }
        return allowlistJson.contains(orderCode.trim());
    }
}
