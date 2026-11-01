package com.theieltsspells.billing.application;

import com.theieltsspells.billing.application.dto.InvoiceAdminDto;
import com.theieltsspells.billing.application.dto.ManualInvoiceRequest;
import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.InvoiceBuyerType;
import com.theieltsspells.billing.domain.PaymentTransaction;
import com.theieltsspells.billing.domain.PaymentTransactionStatus;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.PaymentTransactionRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManualInvoiceApplicationService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final PaymentTransactionRepository transactionRepository;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final ElectronicInvoiceService invoiceService;

    @Transactional
    public InvoiceAdminDto createDraft(ManualInvoiceRequest request, String actor) {
        validateBuyer(request);

        String token = UUID.randomUUID().toString().replace("-", "");
        final BigDecimal amount;
        try {
            amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessRuleException("Số tiền chỉ được có tối đa 2 chữ số thập phân");
        }
        OffsetDateTime paidAt = request.paidDate().atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setGateway("MANUAL");
        transaction.setSepayTransactionId("MANUAL-" + token);
        transaction.setOrderCode("MAN-" + request.paidDate().format(ORDER_DATE) + "-" + token.substring(0, 6).toUpperCase(Locale.ROOT));
        transaction.setAmountIn(amount);
        transaction.setAccumulatedAmount(amount);
        transaction.setTransferContent("Đóng học phí đào tạo IELTS");
        transaction.setPayerName(request.buyerName().trim());
        transaction.setStatus(PaymentTransactionStatus.STANDALONE_PAYMENT);
        transaction.setCreatedAt(paidAt);
        transaction.setReconciliationNote("Tạo giao dịch thủ công bởi " + (actor == null ? "unknown" : actor));
        transaction.setRawPayload(rawPayload(request, actor));
        PaymentTransaction savedTransaction = transactionRepository.save(transaction);

        // Reuse the existing transaction-linked invoice flow; manual invoices are
        // explicitly converted to a local draft before any SePay call is made.
        ElectronicInvoice invoice = invoiceService.initOrGetInvoice(savedTransaction, null);
        invoice.setStatus(com.theieltsspells.billing.domain.InvoiceStatus.DRAFT);
        invoice.setIsDraft(true);
        invoice.setBuyerType(request.buyerType() == InvoiceBuyerType.BUSINESS ? "enterprise" : "personal");
        invoice.setBuyerName(request.buyerName().trim());
        invoice.setBuyerEmail(blankToNull(request.buyerEmail()));
        invoice.setBuyerLegalName(blankToNull(request.companyName()));
        invoice.setBuyerTaxCode(blankToNull(request.taxCode()));
        invoice.setBuyerAddress(blankToNull(request.address()) == null ? "Việt Nam" : request.address().trim());
        invoice.setUpdatedAt(OffsetDateTime.now());
        ElectronicInvoice savedInvoice = invoiceRepository.save(invoice);

        return invoiceService.getInvoiceAdmin(savedInvoice.getId());
    }

    private void validateBuyer(ManualInvoiceRequest request) {
        if (request.buyerType() != InvoiceBuyerType.BUSINESS) {
            return;
        }
        if (blankToNull(request.companyName()) == null) {
            throw new BusinessRuleException("Tên doanh nghiệp là bắt buộc với người mua tổ chức");
        }
        if (blankToNull(request.taxCode()) == null) {
            throw new BusinessRuleException("Mã số thuế là bắt buộc với người mua tổ chức");
        }
    }

    private Map<String, Object> rawPayload(ManualInvoiceRequest request, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "MANUAL_INVOICE");
        payload.put("paidDate", request.paidDate().toString());
        payload.put("buyerType", request.buyerType().name());
        payload.put("createdBy", actor == null ? "unknown" : actor);
        if (blankToNull(request.note()) != null) payload.put("note", request.note().trim());
        return payload;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
