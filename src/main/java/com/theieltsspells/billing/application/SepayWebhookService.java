package com.theieltsspells.billing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.application.EnrollmentApplicationService;
import com.theieltsspells.academic.application.dto.EnrollStudentRequest;
import com.theieltsspells.billing.application.dto.ReconciliationDto;
import com.theieltsspells.billing.application.dto.SepayWebhookPayload;
import com.theieltsspells.billing.domain.*;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.billing.infrastructure.persistence.PaymentTransactionRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.theieltsspells.billing.infrastructure.security.SecretEncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class SepayWebhookService {

    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("(?i)(KH\\d{10,14})");

    private final PaymentTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final BillingSettingRepository billingSettingRepository;
    private final AccountActivationService activationService;
    private final ElectronicInvoiceService invoiceService;
    private final EmailBillingNotificationService emailService;
    private final EnrollmentApplicationService enrollmentService;
    private final ObjectMapper objectMapper;
    private final SecretEncryptionService secretEncryptionService;

    @Autowired
    public SepayWebhookService(
            PaymentTransactionRepository transactionRepository,
            OrderRepository orderRepository,
            BillingSettingRepository billingSettingRepository,
            AccountActivationService activationService,
            ElectronicInvoiceService invoiceService,
            EmailBillingNotificationService emailService,
            EnrollmentApplicationService enrollmentService,
            ObjectMapper objectMapper,
            SecretEncryptionService secretEncryptionService
    ) {
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
        this.billingSettingRepository = billingSettingRepository;
        this.activationService = activationService;
        this.invoiceService = invoiceService;
        this.emailService = emailService;
        this.enrollmentService = enrollmentService;
        this.objectMapper = objectMapper;
        this.secretEncryptionService = secretEncryptionService;
    }

    public SepayWebhookService(
            PaymentTransactionRepository transactionRepository,
            OrderRepository orderRepository,
            BillingSettingRepository billingSettingRepository,
            AccountActivationService activationService,
            ElectronicInvoiceService invoiceService,
            EmailBillingNotificationService emailService,
            EnrollmentApplicationService enrollmentService,
            ObjectMapper objectMapper
    ) {
        this(transactionRepository, orderRepository, billingSettingRepository, activationService, invoiceService, emailService, enrollmentService, objectMapper, new SecretEncryptionService());
    }

    @Transactional
    public Map<String, Object> processWebhook(String authHeader, SepayWebhookPayload payload) {
        validateIncomingPayload(payload);

        BillingSetting settings = billingSettingRepository.findLatest()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Cổng thanh toán chưa được cấu hình"
                ));
        verifyWebhookAuthorization(authHeader, settings);
        verifyDestinationAccount(payload.accountNumber(), settings);

        log.info("Nhận SePay Webhook tiền vào: id={}, gateway={}, amount={}",
                payload.id(), payload.gateway(), payload.transferAmount());

        String transactionIdStr = String.valueOf(payload.id());

        // 2. Idempotency check
        if (transactionRepository.existsBySepayTransactionId(transactionIdStr)) {
            log.info("SePay Webhook: Giao dịch {} đã được xử lý trước đó. Bỏ qua.", transactionIdStr);
            return Map.of("success", true, "message", "Transaction already processed");
        }

        // Convert payload to map for raw logging
        Map<String, Object> rawPayloadMap = objectMapper.convertValue(payload, new TypeReference<>() {});

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setGateway("SEPAY");
        transaction.setSepayTransactionId(transactionIdStr);
        transaction.setAmountIn(payload.transferAmount() != null ? payload.transferAmount() : BigDecimal.ZERO);
        transaction.setAccumulatedAmount(payload.accumulated());
        transaction.setTransferContent(payload.content());
        transaction.setBankBrandName(payload.gateway());
        transaction.setAccountNumber(payload.accountNumber());
        transaction.setRawPayload(rawPayloadMap);
        transaction.setCreatedAt(OffsetDateTime.now());

        // 3. Scan content for Order Code
        String orderCode = extractOrderCode(payload.content());
        transaction.setOrderCode(orderCode);
        transaction.setPayerName(extractPayerName(payload.content(), orderCode));

        if (orderCode == null) {
            log.info("SePay Webhook: Giao dịch QR tĩnh không có mã đơn; lập hóa đơn trực tiếp theo giao dịch {}", transactionIdStr);
            transaction.setStatus(PaymentTransactionStatus.STANDALONE_PAYMENT);
            transaction.setReconciliationNote("Khoản thu học phí qua QR tĩnh; hóa đơn được tự động lập độc lập theo số tiền thực nhận");
            PaymentTransaction saved = transactionRepository.saveAndFlush(transaction);
            queueInvoiceForTransaction(saved, null, settings, "CK");
            return Map.of(
                    "success", true,
                    "status", "STANDALONE_INVOICE_QUEUED",
                    "message", "Static QR payment captured and invoice queued"
            );
        }

        // 4. Lock order row for update
        Optional<Order> orderOpt = orderRepository.findByOrderCodeForUpdate(orderCode);
        if (orderOpt.isEmpty()) {
            log.info("SePay Webhook: Nội dung có mã {} nhưng không tồn tại; đánh dấu cần đối soát và vẫn lập hóa đơn theo tiền thực nhận", orderCode);
            transaction.setStatus(PaymentTransactionStatus.UNMATCHED);
            transaction.setReconciliationNote("Mã đơn " + orderCode + " không tồn tại; hóa đơn được lập độc lập theo giao dịch");
            PaymentTransaction saved = transactionRepository.saveAndFlush(transaction);
            queueInvoiceForTransaction(saved, null, settings, "CK");
            return Map.of("success", true, "status", "UNMATCHED_INVOICE_QUEUED", "message", "Order not found; invoice queued by transaction");
        }

        Order order = orderOpt.get();
        transaction.setOrderId(order.getId());

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED) {
            transaction.setStatus(PaymentTransactionStatus.UNMATCHED);
            transaction.setReconciliationNote("Đơn " + orderCode + " đang ở trạng thái " + order.getStatus() + "; cần kế toán đối soát");
            PaymentTransaction saved = transactionRepository.saveAndFlush(transaction);
            queueInvoiceForTransaction(saved, null, settings, "CK");
            return Map.of("success", true, "status", "REQUIRES_RECONCILIATION", "message", "Order cannot accept payment in current state");
        }

        transaction.setStatus(PaymentTransactionStatus.PARTIAL_PAYMENT);
        PaymentTransaction savedTransaction = transactionRepository.saveAndFlush(transaction);

        BigDecimal previousCaptured = transactionRepository.sumCapturedAmountByOrderIdExcluding(
                order.getId(), savedTransaction.getId());
        BigDecimal accumulated = previousCaptured.add(savedTransaction.getAmountIn());
        savedTransaction.setAccumulatedAmount(accumulated);

        BigDecimal expectedAmount = order.getAmount();
        int comparison = accumulated.compareTo(expectedAmount);
        if (comparison < 0) {
            savedTransaction.setStatus(PaymentTransactionStatus.PARTIAL_PAYMENT);
            savedTransaction.setReconciliationNote(String.format(
                    "Đã nhận cọc/thanh toán một phần %s; lũy kế %s/%s",
                    savedTransaction.getAmountIn(), accumulated, expectedAmount));
        } else if (comparison == 0) {
            savedTransaction.setStatus(PaymentTransactionStatus.SUCCESS);
            savedTransaction.setReconciliationNote("Đã thu đủ học phí theo lũy kế giao dịch");
        } else {
            savedTransaction.setStatus(PaymentTransactionStatus.OVERPAID);
            savedTransaction.setReconciliationNote(String.format(
                    "Tổng tiền nhận vượt %s so với học phí; cần kế toán đối soát",
                    accumulated.subtract(expectedAmount)));
        }
        transactionRepository.save(savedTransaction);

        boolean newlyPaid = order.getStatus() != OrderStatus.PAID && comparison >= 0;
        if (newlyPaid) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(OffsetDateTime.now());
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepository.save(order);
        }

        // Mỗi khoản tiền vào tạo một hóa đơn riêng, kể cả cọc hoặc thanh toán nhiều lần.
        queueInvoiceForTransaction(savedTransaction, order, settings, "CK");

        // 7. Handle Student Enrollment / Activation
        String activationToken = null;
        if (newlyPaid && order.getUserId() != null) {
            // Existing user -> Enroll safely without rolling back payment transaction
            enrollmentService.enrollSafely(new EnrollStudentRequest(
                    order.getCourseId(),
                    order.getUserId(),
                    "Tự động ghi danh sau thanh toán SePay: " + order.getOrderCode()
            ));
        } else if (newlyPaid) {
            // Guest user -> Generate one-time activation token
            activationToken = activationService.generateActivationToken(order);
        }

        if (newlyPaid) {
            emailService.sendPaymentSuccessEmail(order, activationToken);
        }

        return Map.of(
                "success", true,
                "status", savedTransaction.getStatus().name(),
                "orderCode", orderCode,
                "receivedAmount", savedTransaction.getAmountIn(),
                "accumulatedAmount", accumulated,
                "invoiceQueued", true
        );
    }

    @Transactional
    public ReconciliationDto matchOrderManually(UUID transactionId, String orderCode, String note) {
        PaymentTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch"));

        Order order = orderRepository.findByOrderCodeForUpdate(orderCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderCode));

        if (transaction.getOrderId() != null && !transaction.getOrderId().equals(order.getId())) {
            throw new BusinessRuleException("Giao dịch đã được gắn với một đơn hàng khác");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED) {
            throw new BusinessRuleException("Không thể khớp giao dịch vào đơn đã hủy hoặc hoàn tiền");
        }

        transaction.setOrderId(order.getId());
        transaction.setOrderCode(order.getOrderCode());
        transactionRepository.saveAndFlush(transaction);

        BigDecimal previousCaptured = transactionRepository.sumCapturedAmountByOrderIdExcluding(order.getId(), transaction.getId());
        BigDecimal accumulated = previousCaptured.add(transaction.getAmountIn());
        transaction.setAccumulatedAmount(accumulated);
        int comparison = accumulated.compareTo(order.getAmount());
        transaction.setStatus(comparison < 0
                ? PaymentTransactionStatus.PARTIAL_PAYMENT
                : (comparison == 0 ? PaymentTransactionStatus.SUCCESS : PaymentTransactionStatus.OVERPAID));
        transaction.setReconciliationNote("Khớp thủ công bởi kế toán: " + (note != null ? note : ""));
        transactionRepository.save(transaction);

        boolean newlyPaid = order.getStatus() != OrderStatus.PAID && comparison >= 0;
        if (newlyPaid) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(OffsetDateTime.now());
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepository.save(order);
        }

        String activationToken = null;
        if (newlyPaid && order.getUserId() == null) {
            activationToken = activationService.generateActivationToken(order);
        } else if (newlyPaid) {
            enrollmentService.enrollSafely(new EnrollStudentRequest(
                    order.getCourseId(),
                    order.getUserId(),
                    "Kế toán khớp thủ công sau thanh toán: " + order.getOrderCode()
            ));
        }

        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);
        queueInvoiceForTransaction(transaction, order, settings, "CK");

        if (newlyPaid) {
            emailService.sendPaymentSuccessEmail(order, activationToken);
        }

        return toReconciliationDto(transaction);
    }

    @Transactional
    public void confirmManualPayment(UUID orderId, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Chỉ có thể xác nhận thanh toán cho đơn hàng ở trạng thái Chờ thanh toán");
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        PaymentTransaction tx = new PaymentTransaction();
        tx.setGateway("TIEN_MAT");
        tx.setSepayTransactionId("CASH-" + System.currentTimeMillis());
        tx.setOrderId(order.getId());
        tx.setOrderCode(order.getOrderCode());
        tx.setAmountIn(order.getAmount());
        tx.setAccumulatedAmount(order.getAmount());
        tx.setPayerName(order.getCustomerName());
        tx.setTransferContent("Thu tiền mặt trực tiếp tại quầy: " + order.getOrderCode());
        tx.setBankBrandName("Tiền mặt");
        tx.setAccountNumber("TIEN_MAT_QUAY");
        tx.setStatus(PaymentTransactionStatus.SUCCESS);
        tx.setReconciliationNote("Xác nhận tiền mặt bởi quản lý: " + (note != null ? note : ""));
        transactionRepository.save(tx);

        String activationToken = null;
        if (order.getUserId() != null) {
            enrollmentService.enrollSafely(new EnrollStudentRequest(
                    order.getCourseId(),
                    order.getUserId(),
                    "Thu tiền mặt tại quầy: " + order.getOrderCode()
            ));
        } else {
            activationToken = activationService.generateActivationToken(order);
        }

        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);
        queueInvoiceForTransaction(transactionRepository.saveAndFlush(tx), order, settings, "TM");

        emailService.sendPaymentSuccessEmail(order, activationToken);
    }

    public Page<ReconciliationDto> searchTransactions(String query, PaymentTransactionStatus status, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Specification<PaymentTransaction> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromDate != null) {
                OffsetDateTime fromTime = fromDate.atStartOfDay().atOffset(ZoneOffset.ofHours(7));
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
            }
            if (toDate != null) {
                OffsetDateTime toTime = toDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.ofHours(7));
                predicates.add(cb.lessThan(root.get("createdAt"), toTime));
            }
            if (query != null && !query.trim().isEmpty()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("orderCode"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("payerName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("transferContent"), "")), pattern),
                        cb.like(cb.lower(root.get("sepayTransactionId")), pattern)
                ));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        return transactionRepository.findAll(spec, sortedPageable).map(this::toReconciliationDto);
    }

    private String extractOrderCode(String content) {
        if (content == null) return null;
        Matcher matcher = ORDER_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    private void validateIncomingPayload(SepayWebhookPayload payload) {
        if (payload == null || payload.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook thiếu mã giao dịch SePay");
        }
        if (payload.transferAmount() == null || payload.transferAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền giao dịch phải lớn hơn 0");
        }
        if (payload.transferType() == null || !"in".equalsIgnoreCase(payload.transferType().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ chấp nhận giao dịch tiền vào");
        }
    }

    private void verifyWebhookAuthorization(String authHeader, BillingSetting settings) {
        if (settings.getSepayWebhookSecret() == null || settings.getSepayWebhookSecret().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Webhook SePay chưa được cấu hình secret; hệ thống từ chối ghi nhận tiền để bảo vệ dữ liệu"
            );
        }
        String decryptedSecret = secretEncryptionService.decrypt(settings.getSepayWebhookSecret());
        // SePay sends API-key authenticated webhooks as: Authorization: Apikey {key}
        String expected = "Apikey " + decryptedSecret.trim();
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = authHeader == null
                ? new byte[0]
                : authHeader.trim().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            log.warn("SePay Webhook từ chối: Authorization không hợp lệ");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook authentication failed");
        }
    }

    private void verifyDestinationAccount(String receivedAccountNumber, BillingSetting settings) {
        String configured = normalizeAccountNumber(settings.getSepayAccountNumber());
        String received = normalizeAccountNumber(receivedAccountNumber);
        if (configured.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình tài khoản nhận học phí");
        }
        if (received.isBlank() || !MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giao dịch không thuộc tài khoản nhận học phí đã cấu hình");
        }
    }

    private String normalizeAccountNumber(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private String extractPayerName(String transferContent, String orderCode) {
        if (transferContent == null || transferContent.isBlank()) {
            return "Người nộp học phí";
        }
        String normalized = transferContent.trim().replaceAll("\\s+", " ");
        if (orderCode != null) {
            String withoutOrderCode = normalized.replaceAll("(?i)\\b" + Pattern.quote(orderCode) + "\\b", "")
                    .trim()
                    .replaceAll("\\s+", " ");
            if (!withoutOrderCode.isBlank()) {
                normalized = withoutOrderCode;
            }
        }
        // Một số ngân hàng/ZaloPay nối thêm mô tả hệ thống phía sau tên người gửi.
        // Chỉ cắt các cụm phân tách rõ ràng để không tự suy diễn hoặc đổi tên khách.
        String nameOnly = normalized.replaceFirst(
                "(?iu)\\s+(chuyển\\s+khoản|chuyen\\s+khoan|thanh\\s+toán|thanh\\s+toan|đóng\\s+học\\s+phí|dong\\s+hoc\\s+phi)\\b.*$",
                ""
        ).trim();
        if (!nameOnly.isBlank()) {
            normalized = nameOnly;
        }
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private void queueInvoiceForTransaction(
            PaymentTransaction transaction,
            Order order,
            BillingSetting settings,
            String paymentMethod
    ) {
        ElectronicInvoice invoice = invoiceService.initOrGetInvoice(transaction, order);
        invoice.setPaymentMethod(paymentMethod);
        if (Boolean.FALSE.equals(settings.getAutoInvoiceEnabled())) {
            invoiceService.logKillSwitchSkipped(invoice);
        }
        // When enabled, ElectronicInvoiceWorker picks up PENDING_ISSUE after this
        // database transaction commits. No provider call is made inside webhook.
    }

    private ReconciliationDto toReconciliationDto(PaymentTransaction tx) {
        return new ReconciliationDto(
                tx.getId(),
                tx.getGateway(),
                tx.getSepayTransactionId(),
                tx.getOrderId(),
                tx.getOrderCode(),
                tx.getAmountIn(),
                tx.getAccumulatedAmount(),
                tx.getPayerName(),
                tx.getTransferContent(),
                tx.getBankBrandName(),
                tx.getAccountNumber(),
                tx.getStatus(),
                tx.getReconciliationNote(),
                tx.getRawPayload(),
                tx.getCreatedAt()
        );
    }
}
