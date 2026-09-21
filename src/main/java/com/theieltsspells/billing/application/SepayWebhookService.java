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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
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

@Slf4j
@Service
@RequiredArgsConstructor
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

    @Transactional
    public Map<String, Object> processWebhook(String authHeader, SepayWebhookPayload payload) {
        log.info("Nhận SePay Webhook: id={}, gateway={}, amount={}, content='{}'",
                payload.id(), payload.gateway(), payload.transferAmount(), payload.content());

        // 1. Verify webhook authorization header if secret is configured
        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);
        if (settings.getSepayWebhookSecret() != null && !settings.getSepayWebhookSecret().isBlank()) {
            String expected = "Bearer " + settings.getSepayWebhookSecret().trim();
            if (authHeader == null || !authHeader.trim().equals(expected)) {
                log.warn("SePay Webhook từ chối: Header Authorization không hợp lệ");
                throw new BusinessRuleException("Unauthorized SePay Webhook Token");
            }
        }

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

        if (orderCode == null) {
            log.warn("SePay Webhook: Không tìm thấy mã đơn KH... trong nội dung: {}", payload.content());
            transaction.setStatus(PaymentTransactionStatus.UNMATCHED);
            transaction.setReconciliationNote("Không tìm thấy mã đơn hàng trong nội dung chuyển khoản");
            transactionRepository.save(transaction);
            return Map.of("success", true, "status", "UNMATCHED", "message", "No order code found in content");
        }

        // 4. Lock order row for update
        Optional<Order> orderOpt = orderRepository.findByOrderCodeForUpdate(orderCode);
        if (orderOpt.isEmpty()) {
            log.warn("SePay Webhook: Không tìm thấy đơn hàng {} trong hệ thống", orderCode);
            transaction.setStatus(PaymentTransactionStatus.UNMATCHED);
            transaction.setReconciliationNote("Mã đơn " + orderCode + " không tồn tại");
            transactionRepository.save(transaction);
            return Map.of("success", true, "status", "UNMATCHED", "message", "Order not found");
        }

        Order order = orderOpt.get();
        transaction.setOrderId(order.getId());

        // 5. Compare amount
        BigDecimal expectedAmount = order.getAmount();
        BigDecimal receivedAmount = payload.transferAmount() != null ? payload.transferAmount() : BigDecimal.ZERO;

        if (receivedAmount.compareTo(expectedAmount) < 0) {
            log.warn("SePay Webhook: Đơn {} chuyển thiếu tiền! Yêu cầu: {}, Nhận: {}", orderCode, expectedAmount, receivedAmount);
            transaction.setStatus(PaymentTransactionStatus.UNDERPAID);
            transaction.setReconciliationNote(String.format("Chuyển thiếu tiền. Cần: %s, Nhận: %s", expectedAmount, receivedAmount));
            transactionRepository.save(transaction);
            return Map.of("success", true, "status", "UNDERPAID", "message", "Underpaid");
        }

        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Đơn hàng {} đã ở trạng thái PAID từ trước.", orderCode);
            transaction.setStatus(PaymentTransactionStatus.SUCCESS);
            transactionRepository.save(transaction);
            return Map.of("success", true, "status", "ALREADY_PAID");
        }

        // 6. Update Order status to PAID
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        transaction.setStatus(PaymentTransactionStatus.SUCCESS);
        transactionRepository.save(transaction);

        // 7. Handle Student Enrollment / Activation
        String activationToken = null;
        if (order.getUserId() != null) {
            // Existing user -> Enroll immediately
            try {
                enrollmentService.enroll(new EnrollStudentRequest(
                        order.getCourseId(),
                        order.getUserId(),
                        "Tự động ghi danh sau thanh toán SePay: " + order.getOrderCode()
                ));
            } catch (Exception ex) {
                log.warn("Ghi danh học viên hiện tại: {}", ex.getMessage());
            }
        } else {
            // Guest user -> Generate one-time activation token
            activationToken = activationService.generateActivationToken(order);
        }

        // 8. Issue Electronic Invoice
        ElectronicInvoice invoice = null;
        if (Boolean.TRUE.equals(order.getInvoiceRequired())) {
            invoice = invoiceService.issueInvoice(order);
        }

        // 9. Send Dual Email (Activation / Login + e-Invoice)
        emailService.sendPaymentSuccessAndInvoiceEmail(order, invoice, activationToken);

        return Map.of("success", true, "status", "PAID", "orderCode", orderCode);
    }

    @Transactional
    public ReconciliationDto matchOrderManually(UUID transactionId, String orderCode, String note) {
        PaymentTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch"));

        Order order = orderRepository.findByOrderCodeForUpdate(orderCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderCode));

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        transaction.setOrderId(order.getId());
        transaction.setOrderCode(order.getOrderCode());
        transaction.setStatus(PaymentTransactionStatus.SUCCESS);
        transaction.setReconciliationNote("Khớp thủ công bởi kế toán: " + (note != null ? note : ""));
        transactionRepository.save(transaction);

        String activationToken = null;
        if (order.getUserId() == null) {
            activationToken = activationService.generateActivationToken(order);
        } else {
            try {
                enrollmentService.enroll(new EnrollStudentRequest(
                        order.getCourseId(),
                        order.getUserId(),
                        "Kế toán khớp thủ công sau thanh toán: " + order.getOrderCode()
                ));
            } catch (Exception ex) {
                log.warn("Lỗi ghi danh khớp thủ công: {}", ex.getMessage());
            }
        }

        ElectronicInvoice invoice = null;
        if (Boolean.TRUE.equals(order.getInvoiceRequired())) {
            invoice = invoiceService.issueInvoice(order);
        }

        emailService.sendPaymentSuccessAndInvoiceEmail(order, invoice, activationToken);

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
        tx.setTransferContent("Thu tiền mặt trực tiếp tại quầy: " + order.getOrderCode());
        tx.setBankBrandName("Tiền mặt");
        tx.setAccountNumber("TIEN_MAT_QUAY");
        tx.setStatus(PaymentTransactionStatus.SUCCESS);
        tx.setReconciliationNote("Xác nhận tiền mặt bởi quản lý: " + (note != null ? note : ""));
        transactionRepository.save(tx);

        String activationToken = null;
        if (order.getUserId() != null) {
            try {
                enrollmentService.enroll(new EnrollStudentRequest(
                        order.getCourseId(),
                        order.getUserId(),
                        "Thu tiền mặt tại quầy: " + order.getOrderCode()
                ));
            } catch (Exception ex) {
                log.warn("Lỗi ghi danh khi thu tiền mặt: {}", ex.getMessage());
            }
        } else {
            activationToken = activationService.generateActivationToken(order);
        }

        ElectronicInvoice invoice = null;
        if (Boolean.TRUE.equals(order.getInvoiceRequired())) {
            invoice = invoiceService.issueInvoice(order);
        }

        emailService.sendPaymentSuccessAndInvoiceEmail(order, invoice, activationToken);
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

    private ReconciliationDto toReconciliationDto(PaymentTransaction tx) {
        return new ReconciliationDto(
                tx.getId(),
                tx.getGateway(),
                tx.getSepayTransactionId(),
                tx.getOrderId(),
                tx.getOrderCode(),
                tx.getAmountIn(),
                tx.getAccumulatedAmount(),
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
