package com.theieltsspells.billing.application;

import com.theieltsspells.academic.application.BillingCourseQueryService;
import com.theieltsspells.academic.application.BillingCourseView;
import com.theieltsspells.billing.application.dto.*;
import com.theieltsspells.billing.domain.*;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.billing.infrastructure.persistence.AccountActivationTokenRepository;
import com.theieltsspells.identity.application.StudentAccountApplicationService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final BillingCourseQueryService courses;
    private final StudentAccountApplicationService studentAccounts;
    private final BillingSettingRepository billingSettingRepository;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final AccountActivationTokenRepository activationTokenRepository;

    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request) {
        BillingCourseView course = courses.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khóa học với id: " + request.courseId()));

        if (!course.active()) {
            throw new BusinessRuleException("Khóa học hiện không mở đăng ký");
        }

        BigDecimal amount = course.tuitionAmount() != null ? course.tuitionAmount() : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Khóa học chưa được cấu hình học phí hợp lệ");
        }

        // Check if an existing profile matches this email
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<UUID> existingUserId = studentAccounts.findUserIdByEmail(normalizedEmail);

        // Generate unique order code: KH + yyMMdd + 4 random digits
        String orderCode = generateOrderCode();

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusMinutes(30);

        Order order = new Order();
        order.setOrderCode(orderCode);
        order.setCourseId(course.id());
        existingUserId.ifPresent(order::setUserId);
        order.setCustomerName(request.fullName().trim());
        order.setCustomerEmail(normalizedEmail);
        order.setCustomerPhone(request.phone() != null ? request.phone().trim() : null);
        order.setAmount(amount);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setExpiresAt(expiresAt);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        // Invoice information: Always true for B2C/B2B compliance
        order.setInvoiceRequired(true);
        InvoiceBuyerType buyerType = request.buyerType() != null ? request.buyerType() : InvoiceBuyerType.PERSONAL;
        order.setBuyerType(buyerType);
        if (buyerType == InvoiceBuyerType.BUSINESS) {
            order.setInvoiceCompanyName(request.invoiceCompanyName());
            order.setInvoiceTaxCode(request.invoiceTaxCode());
            order.setInvoiceAddress(request.invoiceAddress());
            order.setInvoiceEmail(request.invoiceEmail() != null ? request.invoiceEmail() : normalizedEmail);
        } else {
            order.setInvoiceEmail(normalizedEmail);
        }

        Order saved = orderRepository.save(order);

        // Get bank details for VietQR
        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);
        String bankName = requireConfigured(settings.getSepayBankName(), "ngân hàng nhận tiền");
        String accountNumber = requireConfigured(settings.getSepayAccountNumber(), "số tài khoản nhận tiền");
        String accountName = requireConfigured(settings.getSellerName(), "tên chủ tài khoản");

        String transferContent = saved.getCustomerName() + " " + saved.getOrderCode();
        String qrCodeUrl = generateDynamicVietQrUrl(
                bankName, accountNumber, accountName, saved.getAmount(), transferContent);

        return new CheckoutResponse(
                saved.getId(),
                saved.getOrderCode(),
                course.id(),
                course.name(),
                saved.getAmount(),
                saved.getStatus(),
                qrCodeUrl,
                accountNumber,
                bankName,
                accountName,
                transferContent,
                saved.getExpiresAt()
        );
    }

    @Transactional
    public CheckoutResponse createAdminOrder(AdminCreateOrderRequest request) {
        BillingCourseView course = courses.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khóa học với id: " + request.courseId()));

        if (!course.active()) {
            throw new BusinessRuleException("Khóa học hiện không mở đăng ký");
        }

        BigDecimal amount = (request.amount() != null && request.amount().compareTo(BigDecimal.ZERO) > 0)
                ? request.amount()
                : (course.tuitionAmount() != null ? course.tuitionAmount() : BigDecimal.ZERO);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Số tiền học phí phải lớn hơn 0");
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<UUID> existingUserId = studentAccounts.findUserIdByEmail(normalizedEmail);

        String orderCode = generateOrderCode();
        OffsetDateTime now = OffsetDateTime.now();
        int hours = (request.expiresInHours() != null && request.expiresInHours() > 0) ? request.expiresInHours() : 48;
        OffsetDateTime expiresAt = now.plusHours(hours);

        Order order = new Order();
        order.setOrderCode(orderCode);
        order.setCourseId(course.id());
        existingUserId.ifPresent(order::setUserId);
        order.setCustomerName(request.fullName().trim());
        order.setCustomerEmail(normalizedEmail);
        order.setCustomerPhone(request.phone() != null ? request.phone().trim() : null);
        order.setAmount(amount);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setExpiresAt(expiresAt);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        boolean invRequired = request.invoiceRequired() == null || request.invoiceRequired();
        order.setInvoiceRequired(invRequired);
        order.setBuyerType(request.buyerType() != null ? request.buyerType() : InvoiceBuyerType.PERSONAL);
        order.setInvoiceCompanyName(request.invoiceCompanyName() != null && !request.invoiceCompanyName().isBlank() ? request.invoiceCompanyName().trim() : null);
        order.setInvoiceTaxCode(request.invoiceTaxCode() != null && !request.invoiceTaxCode().isBlank() ? request.invoiceTaxCode().trim() : null);
        order.setInvoiceAddress(request.invoiceAddress() != null && !request.invoiceAddress().isBlank() ? request.invoiceAddress().trim() : null);
        order.setInvoiceEmail(request.invoiceEmail() != null && !request.invoiceEmail().isBlank()
                ? request.invoiceEmail().trim().toLowerCase(Locale.ROOT)
                : normalizedEmail);

        Order saved = orderRepository.save(order);

        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);
        String bankName = requireConfigured(settings.getSepayBankName(), "ngân hàng nhận tiền");
        String accountNumber = requireConfigured(settings.getSepayAccountNumber(), "số tài khoản nhận tiền");
        String accountName = requireConfigured(settings.getSellerName(), "tên chủ tài khoản");

        String transferContent = saved.getCustomerName() + " " + saved.getOrderCode();
        String qrCodeUrl = generateDynamicVietQrUrl(
                bankName, accountNumber, accountName, saved.getAmount(), transferContent);

        return new CheckoutResponse(
                saved.getId(),
                saved.getOrderCode(),
                course.id(),
                course.name(),
                saved.getAmount(),
                saved.getStatus(),
                qrCodeUrl,
                accountNumber,
                bankName,
                accountName,
                transferContent,
                saved.getExpiresAt()
        );
    }

    public List<CourseSummaryDto> getActiveCoursesSummary() {
        return courses.findActive().stream()
                .map(c -> new CourseSummaryDto(
                        c.id(),
                        c.code(),
                        c.name(),
                        c.tuitionAmount(),
                        c.level()
                ))
                .toList();
    }

    /**
     * QR tài khoản doanh nghiệp dùng lâu dài. QR này không tạo đơn hàng, không
     * gắn khóa học và không khóa số tiền; mọi khoản tiền vào vẫn được webhook
     * ghi nhận và lập hóa đơn độc lập theo số tiền thực nhận.
     */
    public StaticQrResponse getStaticTuitionQr() {
        BillingSetting settings = billingSettingRepository.findLatest().orElseGet(BillingSetting::new);
        String bankName = requireConfigured(settings.getSepayBankName(), "ngân hàng nhận tiền");
        String accountNumber = requireConfigured(settings.getSepayAccountNumber(), "số tài khoản nhận tiền");
        String accountName = requireConfigured(settings.getSellerName(), "tên chủ tài khoản");

        return new StaticQrResponse(
                generateStaticVietQrUrl(bankName, accountNumber, accountName),
                accountNumber,
                bankName,
                accountName,
                "Họ và tên học viên",
                "Đóng học phí đào tạo IELTS"
        );
    }

    public OrderStatusResponse getOrderStatus(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderCode));

        // Auto check expiration
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && order.getExpiresAt().isBefore(OffsetDateTime.now())) {
            order.setStatus(OrderStatus.EXPIRED);
        }

        BillingCourseView course = courses.findById(order.getCourseId()).orElse(null);
        String courseTitle = course != null ? course.name() : "Khóa học IELTS";

        ElectronicInvoice invoice = invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);

        return new OrderStatusResponse(
                order.getOrderCode(),
                order.getStatus(),
                accountActivationStatus(order),
                order.getAmount(),
                order.getPaidAt(),
                courseTitle,
                invoice != null ? invoice.getLookupUrl() : null,
                invoice != null ? invoice.getPdfUrl() : null
        );
    }

    public Page<OrderAdminDto> searchOrders(String query, OrderStatus status, UUID courseId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Specification<Order> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (courseId != null) {
                predicates.add(cb.equal(root.get("courseId"), courseId));
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
                        cb.like(cb.lower(root.get("orderCode")), pattern),
                        cb.like(cb.lower(root.get("customerName")), pattern),
                        cb.like(cb.lower(root.get("customerEmail")), pattern)
                ));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };

        // Build the requested page only after merging both data sources. Fetching
        // a single order page first would drop normal orders when a QR-tĩnh row
        // is inserted before them in the merged result.
        List<OrderAdminDto> unified = new ArrayList<>(
                orderRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"))
                        .stream()
                        .map(this::toAdminDto)
                        .toList()
        );

        // QR tĩnh không có order_id, nhưng hóa đơn của nó vẫn phải xuất hiện
        // trong cùng bảng quản trị. Đây là dòng hiển thị ảo, không phải đơn
        // khóa học giả và không thể kích hoạt quyền học.
        invoiceRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(invoice -> invoice.getOrderId() == null)
                .filter(invoice -> matchesStandaloneInvoice(invoice, query, status, courseId, fromDate, toDate))
                .map(this::toStandaloneAdminDto)
                .forEach(unified::add);

        unified.sort(Comparator.comparing(
                OrderAdminDto::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        int fromIndex = (int) Math.min(pageable.getOffset(), unified.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), unified.size());
        return new PageImpl<>(unified.subList(fromIndex, toIndex), pageable, unified.size());
    }

    private boolean matchesStandaloneInvoice(
            ElectronicInvoice invoice,
            String query,
            OrderStatus status,
            UUID courseId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (status != null && status != OrderStatus.PAID) return false;
        if (courseId != null) return false;

        OffsetDateTime createdAt = invoice.getCreatedAt();
        if (fromDate != null && createdAt != null
                && createdAt.isBefore(fromDate.atStartOfDay().atOffset(ZoneOffset.ofHours(7)))) return false;
        if (toDate != null && createdAt != null
                && !createdAt.isBefore(toDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.ofHours(7)))) return false;

        if (query == null || query.isBlank()) return true;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        PaymentTransaction transaction = invoice.getPaymentTransaction();
        String transactionCode = transaction != null ? transaction.getSepayTransactionId() : "";
        String transferContent = transaction != null ? transaction.getTransferContent() : "";
        String payerName = transaction != null ? transaction.getPayerName() : "";
        return contains(needle, "qr-tinh")
                || contains(needle, "static-" + transactionCode)
                || contains(needle, invoice.getReferenceCode())
                || contains(needle, invoice.getInvoiceNumber())
                || contains(needle, invoice.getCqtCode())
                || contains(needle, invoice.getLookupCode())
                || contains(needle, invoice.getBuyerName())
                || contains(needle, invoice.getBuyerEmail())
                || contains(needle, payerName)
                || contains(needle, transferContent);
    }

    private boolean contains(String needle, String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private OrderAdminDto toStandaloneAdminDto(ElectronicInvoice invoice) {
        PaymentTransaction transaction = invoice.getPaymentTransaction();
        String transactionCode = transaction != null && transaction.getSepayTransactionId() != null
                ? transaction.getSepayTransactionId()
                : invoice.getPaymentTransactionId() != null ? invoice.getPaymentTransactionId().toString() : "UNKNOWN";
        String customerName = firstNonBlank(
                invoice.getBuyerName(),
                transaction != null ? transaction.getPayerName() : null,
                "Khách thanh toán QR tĩnh"
        );
        String customerEmail = firstNonBlank(invoice.getBuyerEmail(), "");
        BigDecimal amount = invoice.getTotalAmount() != null
                ? invoice.getTotalAmount()
                : transaction != null ? transaction.getAmountIn() : BigDecimal.ZERO;
        OffsetDateTime paidAt = transaction != null && transaction.getCreatedAt() != null
                ? transaction.getCreatedAt()
                : invoice.getIssuedAt() != null ? invoice.getIssuedAt() : invoice.getCreatedAt();
        InvoiceBuyerType buyerType = InvoiceBuyerType.PERSONAL;
        if (invoice.getBuyerType() != null) {
            try {
                buyerType = InvoiceBuyerType.valueOf(invoice.getBuyerType().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Keep PERSONAL for legacy/static invoices with no buyer type.
            }
        }

        return new OrderAdminDto(
                invoice.getId(),
                "STATIC-" + transactionCode,
                null,
                invoice.getProductName() != null ? invoice.getProductName() : "Thanh toán QR tĩnh",
                null,
                customerName,
                customerEmail,
                invoice.getBuyerPhone(),
                amount,
                OrderStatus.PAID,
                null,
                paidAt,
                paidAt,
                true,
                buyerType,
                invoice.getBuyerLegalName(),
                invoice.getBuyerTaxCode(),
                invoice.getBuyerAddress(),
                invoice.getBuyerEmail(),
                invoice.getId(),
                invoice.getStatus(),
                invoice.getErrorCategory(),
                invoice.getReconciliationStatus(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceTemplate(),
                invoice.getCqtCode(),
                invoice.getLookupUrl(),
                invoice.getPdfUrl(),
                invoice.getPilotApproved(),
                invoice.getCreatedAt(),
                true
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    public OrderAdminDto getOrderAdmin(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        return toAdminDto(order);
    }

    @Transactional
    public OrderAdminDto cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Chỉ có thể hủy đơn hàng ở trạng thái Chờ thanh toán");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(OffsetDateTime.now());
        return toAdminDto(order);
    }

    private OrderAdminDto toAdminDto(Order order) {
        BillingCourseView course = courses.findById(order.getCourseId()).orElse(null);
        ElectronicInvoice invoice = invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);

        return new OrderAdminDto(
                order.getId(),
                order.getOrderCode(),
                order.getCourseId(),
                course != null ? course.name() : "Khóa học",
                order.getUserId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getCustomerPhone(),
                order.getAmount(),
                order.getStatus(),
                accountActivationStatus(order),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getInvoiceRequired(),
                order.getBuyerType(),
                order.getInvoiceCompanyName(),
                order.getInvoiceTaxCode(),
                order.getInvoiceAddress(),
                order.getInvoiceEmail(),
                invoice != null ? invoice.getId() : null,
                invoice != null ? invoice.getStatus() : null,
                invoice != null ? invoice.getErrorCategory() : null,
                invoice != null ? invoice.getReconciliationStatus() : null,
                invoice != null ? invoice.getInvoiceNumber() : null,
                invoice != null ? invoice.getInvoiceTemplate() : null,
                invoice != null ? invoice.getCqtCode() : null,
                invoice != null ? invoice.getLookupUrl() : null,
                invoice != null ? invoice.getPdfUrl() : null,
                order.getPilotApproved(),
                order.getCreatedAt(),
                false
        );
    }

    private AccountActivationStatus accountActivationStatus(Order order) {
        if (order.getStatus() != OrderStatus.PAID) return null;
        return activationTokenRepository.findByOrderId(order.getId())
                .filter(token -> !token.isUsed())
                .map(token -> AccountActivationStatus.WAITING_ACTIVATION)
                .orElse(AccountActivationStatus.ACTIVATED);
    }

    private String generateOrderCode() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        for (int i = 0; i < 10; i++) {
            int randomNum = ThreadLocalRandom.current().nextInt(1000, 9999);
            String candidate = "KH" + datePrefix + randomNum;
            if (orderRepository.findByOrderCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "KH" + System.currentTimeMillis();
    }

    private String generateStaticVietQrUrl(String bank, String accountNo, String accountName) {
        // QR tĩnh: học viên tự nhập số tiền (cọc/đóng nhiều lần) và nội dung chuyển khoản.
        String cleanBank = bank.replaceAll("\\s+", "");
        String encodedAccountName = URLEncoder.encode(accountName, StandardCharsets.UTF_8);

        return String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?accountName=%s",
                cleanBank, accountNo, encodedAccountName
        );
    }

    private String generateDynamicVietQrUrl(
            String bank,
            String accountNo,
            String accountName,
            BigDecimal amount,
            String transferContent
    ) {
        String cleanBank = bank.replaceAll("\\s+", "");
        String encodedAccountName = URLEncoder.encode(accountName, StandardCharsets.UTF_8);
        String encodedTransferContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        String encodedAmount = amount.stripTrailingZeros().toPlainString();

        return String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                cleanBank, accountNo, encodedAmount, encodedTransferContent, encodedAccountName
        );
    }

    private String requireConfigured(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("Chưa cấu hình " + fieldName + " cho QR học phí");
        }
        return value.trim();
    }
}
