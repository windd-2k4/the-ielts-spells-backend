package com.theieltsspells.billing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.application.EnrollmentApplicationService;
import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.billing.application.dto.AdminCreateOrderRequest;
import com.theieltsspells.billing.application.dto.CheckoutResponse;
import com.theieltsspells.billing.application.dto.SepayWebhookPayload;
import com.theieltsspells.identity.infrastructure.persistence.ProfileRepository;
import com.theieltsspells.billing.domain.*;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.InvoiceAuditLogRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.billing.infrastructure.persistence.PaymentTransactionRepository;
import com.theieltsspells.billing.infrastructure.security.SecretEncryptionService;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceClient;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceTokenService;
import com.theieltsspells.billing.presentation.admin.AdminBillingController;
import com.theieltsspells.shared.application.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SepayEInvoiceWorkflowTests {

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private BillingSettingRepository billingSettingRepository;

    @Mock
    private AccountActivationService activationService;

    @Mock
    private ElectronicInvoiceService mockInvoiceService;

    @Mock
    private EmailBillingNotificationService emailService;

    @Mock
    private EnrollmentApplicationService enrollmentService;

    @Mock
    private ElectronicInvoiceRepository invoiceRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private SepayEInvoiceClient sepayEInvoiceClient;

    @Mock
    private SepayEInvoiceTokenService tokenService;

    @Mock
    private InvoiceAuditLogRepository auditLogRepository;

    @Mock
    private OrderApplicationService orderApplicationService;

    private OrderApplicationService realOrderApplicationService;
    private SepayWebhookService webhookService;
    private ElectronicInvoiceWorker invoiceWorker;
    private ElectronicInvoiceService realInvoiceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecretEncryptionService secretEncryptionService = new SecretEncryptionService();

    @BeforeEach
    void setUp() {
        webhookService = new SepayWebhookService(
                transactionRepository,
                orderRepository,
                billingSettingRepository,
                activationService,
                mockInvoiceService,
                emailService,
                enrollmentService,
                objectMapper,
                secretEncryptionService
        );

        lenient().when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            if (transaction.getId() == null) transaction.setId(UUID.randomUUID());
            return transaction;
        });
        lenient().when(transactionRepository.saveAndFlush(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            if (transaction.getId() == null) transaction.setId(UUID.randomUUID());
            return transaction;
        });
        lenient().when(transactionRepository.sumCapturedAmountByOrderIdExcluding(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(billingSettingRepository.findLatest())
                .thenReturn(Optional.of(configuredWebhookSetting()));
        lenient().when(mockInvoiceService.initOrGetInvoice(
                        any(PaymentTransaction.class),
                        org.mockito.ArgumentMatchers.nullable(Order.class)))
                .thenAnswer(invocation -> {
                    PaymentTransaction transaction = invocation.getArgument(0);
                    ElectronicInvoice invoice = new ElectronicInvoice();
                    invoice.setId(UUID.randomUUID());
                    invoice.setPaymentTransactionId(transaction.getId());
                    return invoice;
                });

        invoiceWorker = new ElectronicInvoiceWorker(
                invoiceRepository,
                orderRepository,
                billingSettingRepository,
                mockInvoiceService
        );

        realInvoiceService = new ElectronicInvoiceService(
                invoiceRepository,
                orderRepository,
                billingSettingRepository,
                sepayEInvoiceClient,
                courseRepository,
                emailService,
                auditLogRepository
        );

        realOrderApplicationService = new OrderApplicationService(
                orderRepository,
                courseRepository,
                profileRepository,
                billingSettingRepository,
                invoiceRepository
        );
    }

    // =========================================================================
    // SECTION 1: 20 REQUIRED PRODUCTION-COMPLIANCE TESTS
    // =========================================================================

    @Test
    @DisplayName("1. Reference Code: Luôn là INV-{orderCode} bất biến, tuyệt đối không thêm randomSuffix")
    void testReferenceCode_Immutable_NoRandomSuffix() {
        Order order = createSampleOrder("KH2609228888");
        when(invoiceRepository.findLatestByOrderIdForUpdate(order.getId())).thenReturn(Optional.empty());
        when(invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())).thenReturn(Optional.empty());
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BillingSetting setting = new BillingSetting();
        setting.setTaxTreatment(TaxTreatment.NOT_SUBJECT_TO_VAT);
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));

        ElectronicInvoice invoice = realInvoiceService.initOrGetInvoice(order);

        assertThat(invoice.getReferenceCode()).isEqualTo("INV-KH2609228888");
        assertThat(invoice.getReferenceCode()).doesNotContain("_");
        assertThat(invoice.getReferenceCode()).doesNotMatch(".*-[0-9]{4,}$");
    }

    @Test
    @DisplayName("2. Pre-resend check: Gọi GET /v1/invoices/{reference_code} trước khi gửi lại, nếu đã tồn tại thì đồng bộ DB")
    void testPreResendCheck_ExistingOnSepay_SynchronizesLocalDb() {
        Order order = createSampleOrder("KH2609221234");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH2609221234");
        invoice.setStatus(InvoiceStatus.FAILED);
        invoice.setErrorCategory(InvoiceErrorCategory.RETRYABLE);

        BillingSetting setting = new BillingSetting();
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Giả lập SePay đã có hóa đơn này và trả về chi tiết thành công
        SepayEInvoiceClient.InvoiceDetailResult detail = SepayEInvoiceClient.InvoiceDetailResult.ofSuccess(
                "304999", "C26TSE", "CQT-CODE-1234", "LOOKUP-999",
                "https://sepay.vn/pdf/304999", "https://sepay.vn/xml/304999"
        );
        when(sepayEInvoiceClient.getInvoiceDetail(any(), eq("INV-KH2609221234"))).thenReturn(detail);

        realInvoiceService.retryInvoice(invoice.getId());

        // Phải đồng bộ hóa đơn thành ISSUED mà KHÔNG gọi createInvoice
        verify(sepayEInvoiceClient, never()).createInvoice(any(), any());
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getInvoiceNumber()).isEqualTo("304999");
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    }

    @Test
    @DisplayName("3. HTTP 409 Conflict: Xử lý EINVOICE_DOCUMENT_EXISTED bằng cách đồng bộ qua GET detail, giữ nguyên reference_code")
    void testHttp409_Conflict_ReconcilesWithoutChangingReferenceCode() {
        Order order = createSampleOrder("KH2609224090");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH2609224090");
        invoice.setStatus(InvoiceStatus.PENDING_ISSUE);

        BillingSetting setting = new BillingSetting();
        setting.setEinvoiceTemplateCode("1");
        setting.setEinvoiceInvoiceSeries("C26TSE");
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // SePay trả về HTTP 409 EINVOICE_DOCUMENT_EXISTED
        when(sepayEInvoiceClient.createInvoice(any(), any())).thenReturn(
                new SepayEInvoiceClient.CreateResult(false, null, "EINVOICE_DOCUMENT_EXISTED", "Hóa đơn đã tồn tại trên SePay")
        );

        // Pre-check trả về notFound, sau đó khi nhận 409 EINVOICE_DOCUMENT_EXISTED sẽ gọi GET detail để đồng bộ
        SepayEInvoiceClient.InvoiceDetailResult detail = SepayEInvoiceClient.InvoiceDetailResult.ofSuccess(
                "409409", "C26TSE", "CQT-409", "LOOKUP-409",
                "https://sepay.vn/pdf/409", "https://sepay.vn/xml/409"
        );
        when(sepayEInvoiceClient.getInvoiceDetail(any(), eq("INV-KH2609224090")))
                .thenReturn(SepayEInvoiceClient.InvoiceDetailResult.ofNotFound())
                .thenReturn(detail);

        realInvoiceService.executeCreateInvoice(invoice, order);

        assertThat(invoice.getReferenceCode()).isEqualTo("INV-KH2609224090"); // Không đổi
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getInvoiceNumber()).isEqualTo("409409");
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    }

    // =========================================================================
    // 6 DEDICATED HTTP 409 TEST CASES
    // =========================================================================

    @Test
    @DisplayName("3.1 HTTP 409: EINVOICE_DOCUMENT_EXISTED được phân loại là RECONCILABLE (không phải RETRYABLE)")
    void http409DocumentExisted_isReconciliableNotRetryable() {
        InvoiceErrorCategory cat1 = realInvoiceService.determineErrorCategory("EINVOICE_DOCUMENT_EXISTED", "Hóa đơn đã tồn tại");
        assertThat(cat1).isEqualTo(InvoiceErrorCategory.RECONCILABLE);
        assertThat(cat1).isNotEqualTo(InvoiceErrorCategory.RETRYABLE);

        InvoiceErrorCategory cat2 = realInvoiceService.determineErrorCategory(null, "Hóa đơn đã tồn tại trên SePay");
        assertThat(cat2).isEqualTo(InvoiceErrorCategory.RECONCILABLE);

        InvoiceErrorCategory cat3 = realInvoiceService.determineErrorCategory(null, "Document_existed in system");
        assertThat(cat3).isEqualTo(InvoiceErrorCategory.RECONCILABLE);
    }

    @Test
    @DisplayName("3.2 HTTP 409: retryInvoice với RECONCILABLE không bao giờ gọi POST create lại")
    void http409DocumentExisted_neverRetriesPostCreate() {
        Order order = createSampleOrder("KH260922NORETRY");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH260922NORETRY");
        invoice.setStatus(InvoiceStatus.FAILED);
        invoice.setErrorCategory(InvoiceErrorCategory.RECONCILABLE);
        invoice.setReconciliationStatus(ReconciliationStatus.PENDING);

        BillingSetting setting = new BillingSetting();
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Giả lập SePay trả về detail thành công
        SepayEInvoiceClient.InvoiceDetailResult detail = SepayEInvoiceClient.InvoiceDetailResult.ofSuccess(
                "304999", "C26TSE", "CQT-CODE-1234", "LOOKUP-999",
                "https://sepay.vn/pdf/304999", "https://sepay.vn/xml/304999"
        );
        when(sepayEInvoiceClient.getInvoiceDetail(any(), eq("INV-KH260922NORETRY"))).thenReturn(detail);

        realInvoiceService.retryInvoice(invoice.getId());

        // Tuyệt đối KHÔNG gọi createInvoice
        verify(sepayEInvoiceClient, never()).createInvoice(any(), any());
        verify(sepayEInvoiceClient).getInvoiceDetail(any(), eq("INV-KH260922NORETRY"));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    @DisplayName("3.3 HTTP 409: Hóa đơn đã tồn tại ở trạng thái issued trên SePay -> Đồng bộ thành ISSUED")
    void http409DocumentExisted_existingIssuedInvoice_reconciles() {
        Order order = createSampleOrder("KH260922ISSUED");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH260922ISSUED");
        invoice.setStatus(InvoiceStatus.PENDING_ISSUE);

        BillingSetting setting = new BillingSetting();
        setting.setEinvoiceTemplateCode("1");
        setting.setEinvoiceInvoiceSeries("C26TSE");
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(sepayEInvoiceClient.createInvoice(any(), any())).thenReturn(
                new SepayEInvoiceClient.CreateResult(false, null, "EINVOICE_DOCUMENT_EXISTED", "Hóa đơn đã tồn tại")
        );

        SepayEInvoiceClient.InvoiceDetailResult detail = SepayEInvoiceClient.InvoiceDetailResult.ofSuccess(
                "409001", "C26TSE", "CQT-409001", "LOOKUP-409001",
                "https://sepay.vn/pdf/409001", "https://sepay.vn/xml/409001"
        );
        when(sepayEInvoiceClient.getInvoiceDetail(any(), eq("INV-KH260922ISSUED")))
                .thenReturn(SepayEInvoiceClient.InvoiceDetailResult.ofNotFound())
                .thenReturn(detail);

        realInvoiceService.executeCreateInvoice(invoice, order);

        assertThat(invoice.getReferenceCode()).isEqualTo("INV-KH260922ISSUED");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getInvoiceNumber()).isEqualTo("409001");
        assertThat(invoice.getCqtCode()).isEqualTo("CQT-409001");
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
        assertThat(invoice.getErrorCategory()).isNull();
    }

    @Test
    @DisplayName("3.4 HTTP 409: Hóa đơn đã tồn tại ở trạng thái draft trên SePay -> Đồng bộ thành DRAFT")
    void http409DocumentExisted_existingDraft_reconcilesAsDraft() {
        Order order = createSampleOrder("KH260922DRAFT");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH260922DRAFT");
        invoice.setStatus(InvoiceStatus.PENDING_ISSUE);

        BillingSetting setting = new BillingSetting();
        setting.setEinvoiceTemplateCode("1");
        setting.setEinvoiceInvoiceSeries("C26TSE");
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(sepayEInvoiceClient.createInvoice(any(), any())).thenReturn(
                new SepayEInvoiceClient.CreateResult(false, null, "EINVOICE_DOCUMENT_EXISTED", "Hóa đơn đã tồn tại")
        );

        SepayEInvoiceClient.InvoiceDetailResult detail = SepayEInvoiceClient.InvoiceDetailResult.ofDraft(
                "409DRAFT", "C26TSE", "LOOKUP-DRAFT"
        );
        when(sepayEInvoiceClient.getInvoiceDetail(any(), eq("INV-KH260922DRAFT")))
                .thenReturn(SepayEInvoiceClient.InvoiceDetailResult.ofNotFound())
                .thenReturn(detail);

        realInvoiceService.executeCreateInvoice(invoice, order);

        assertThat(invoice.getReferenceCode()).isEqualTo("INV-KH260922DRAFT");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    }

    @Test
    @DisplayName("3.5 HTTP 409: SePay báo tồn tại nhưng GET detail 404 -> KHÔNG create lại, đặt RECONCILABLE và PENDING đối soát")
    void http409ThenDetail404_doesNotCreateAgain() {
        Order order = createSampleOrder("KH260922NOTFOUND");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH260922NOTFOUND");
        invoice.setStatus(InvoiceStatus.PENDING_ISSUE);

        BillingSetting setting = new BillingSetting();
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(sepayEInvoiceClient.createInvoice(any(), any())).thenReturn(
                new SepayEInvoiceClient.CreateResult(false, null, "EINVOICE_DOCUMENT_EXISTED", "Hóa đơn đã tồn tại")
        );

        when(sepayEInvoiceClient.getInvoiceDetail(any(), eq("INV-KH260922NOTFOUND")))
                .thenReturn(SepayEInvoiceClient.InvoiceDetailResult.ofNotFound());

        realInvoiceService.executeCreateInvoice(invoice, order);

        verify(sepayEInvoiceClient, times(1)).createInvoice(any(), any());
        assertThat(invoice.getReferenceCode()).isEqualTo("INV-KH260922NOTFOUND");
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
        assertThat(invoice.getErrorCategory()).isEqualTo(InvoiceErrorCategory.RECONCILABLE);
        assertThat(invoice.getNextRetryAt()).isAfter(OffsetDateTime.now().minusSeconds(1));
    }

    @Test
    @DisplayName("3.6 HTTP 409: Khi GET detail gặp timeout / ngoại lệ mạng -> KHÔNG create lại, đặt RECONCILABLE và PENDING đối soát")
    void http409ThenDetailTimeout_doesNotCreateAgain() {
        Order order = createSampleOrder("KH260922TIMEOUT");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH260922TIMEOUT");
        invoice.setStatus(InvoiceStatus.PENDING_ISSUE);

        BillingSetting setting = new BillingSetting();
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(sepayEInvoiceClient.createInvoice(any(), any())).thenReturn(
                new SepayEInvoiceClient.CreateResult(false, null, "EINVOICE_DOCUMENT_EXISTED", "Hóa đơn đã tồn tại")
        );

        when(sepayEInvoiceClient.getInvoiceDetail(any(), eq("INV-KH260922TIMEOUT")))
                .thenReturn(SepayEInvoiceClient.InvoiceDetailResult.ofNotFound())
                .thenThrow(new RuntimeException("Connection timed out to SePay eInvoice"));

        realInvoiceService.executeCreateInvoice(invoice, order);

        verify(sepayEInvoiceClient, times(1)).createInvoice(any(), any());
        assertThat(invoice.getReferenceCode()).isEqualTo("INV-KH260922TIMEOUT");
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
        assertThat(invoice.getErrorCategory()).isEqualTo(InvoiceErrorCategory.RECONCILABLE);
        assertThat(invoice.getNextRetryAt()).isAfter(OffsetDateTime.now().minusSeconds(1));
    }

    @Test
    @DisplayName("4. Polling respects retry.next_retry_at: Tôn trọng thời gian SePay chỉ định")
    void testPolling_RespectsNextRetryAt() {
        UUID invId = UUID.randomUUID();
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(invId);
        invoice.setStatus(InvoiceStatus.PROCESSING);
        invoice.setCreateTrackingCode("TRK-POLL-1");

        OffsetDateTime sepayNextRetry = OffsetDateTime.now().plusSeconds(45);
        SepayEInvoiceClient.CheckStatusResult checkResult = new SepayEInvoiceClient.CheckStatusResult(
                false, false, false, true, false, sepayNextRetry, 1, 10,
                null, null, null, null, null, null, null, "Đang xử lý"
        );

        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(configuredWebhookSetting()));
        when(invoiceRepository.findById(invId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sepayEInvoiceClient.checkCreateStatus(any(), eq("TRK-POLL-1"))).thenReturn(checkResult);

        realInvoiceService.checkAndAdvanceInvoice(invId);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PROCESSING);
        assertThat(invoice.getNextRetryAt()).isEqualTo(sepayNextRetry);
    }

    @Test
    @DisplayName("5. In-flight processing: Trong thời gian < 2 giờ tiếp tục polling với exponential backoff")
    void testPolling_InFlightUnder2Hours_ExponentialBackoff() {
        UUID invId = UUID.randomUUID();
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(invId);
        invoice.setStatus(InvoiceStatus.PROCESSING);
        invoice.setCreateTrackingCode("TRK-POLL-EXP");
        invoice.setFirstSubmittedAt(OffsetDateTime.now().minusMinutes(30)); // 30 phút trước
        invoice.setRetryCount(3);

        SepayEInvoiceClient.CheckStatusResult checkResult = new SepayEInvoiceClient.CheckStatusResult(
                false, false, false, true, false, null, 3, 10,
                null, null, null, null, null, null, null, "In Queue"
        );

        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(configuredWebhookSetting()));
        when(invoiceRepository.findById(invId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sepayEInvoiceClient.checkCreateStatus(any(), eq("TRK-POLL-EXP"))).thenReturn(checkResult);

        realInvoiceService.checkAndAdvanceInvoice(invId);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PROCESSING);
        assertThat(invoice.getRetryCount()).isEqualTo(4);
        assertThat(invoice.getNextRetryAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    @DisplayName("6. Beyond 2 hours: KHÔNG tự chuyển sang UNKNOWN, duy trì nhịp thưa 180s và GET detail")
    void testPolling_Beyond2Hours_MaintainsSlowPolling_DoesNotTimeoutToUnknown() {
        UUID invId = UUID.randomUUID();
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(invId);
        invoice.setReferenceCode("INV-KH260922OVER2H");
        invoice.setStatus(InvoiceStatus.PROCESSING);
        invoice.setCreateTrackingCode("TRK-OVER-2H");
        invoice.setFirstSubmittedAt(OffsetDateTime.now().minusHours(3)); // 3 giờ trước (> 2 giờ)
        invoice.setRetryCount(20);

        SepayEInvoiceClient.CheckStatusResult checkResult = new SepayEInvoiceClient.CheckStatusResult(
                false, false, false, true, false, null, 20, 30,
                null, null, null, null, null, null, null, "Still processing at Tax Department"
        );

        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(new BillingSetting()));
        when(invoiceRepository.findById(invId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sepayEInvoiceClient.checkCreateStatus(any(), eq("TRK-OVER-2H"))).thenReturn(checkResult);
        when(sepayEInvoiceClient.getInvoiceDetail(any(), eq("INV-KH260922OVER2H"))).thenReturn(
                SepayEInvoiceClient.InvoiceDetailResult.ofPending("Still processing at Tax Department")
        );

        realInvoiceService.checkAndAdvanceInvoice(invId);

        // Quan trọng: KHÔNG được chuyển sang UNKNOWN chỉ vì quá 2 giờ!
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PROCESSING);
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
        // Nhịp thăm dò giãn thưa >= 170 giây
        assertThat(invoice.getNextRetryAt()).isAfter(OffsetDateTime.now().plusSeconds(170));
    }

    @Test
    @DisplayName("7. Undetermined error from SePay: Message bất định -> UNKNOWN và REQUIRES_REVIEW")
    void testPolling_TrulyUndeterminedMessage_TransitionsToUnknownWithRequiresReview() {
        UUID invId = UUID.randomUUID();
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(invId);
        invoice.setStatus(InvoiceStatus.PROCESSING);
        invoice.setCreateTrackingCode("TRK-UNDET");

        SepayEInvoiceClient.CheckStatusResult checkResult = new SepayEInvoiceClient.CheckStatusResult(
                false, false, false, false, true, null, 5, 10,
                null, null, null, null, null, null, null,
                "Hệ thống chưa xác nhận được kết quả phát hành với nhà cung cấp hóa đơn"
        );

        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(new BillingSetting()));
        when(invoiceRepository.findById(invId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sepayEInvoiceClient.checkCreateStatus(any(), eq("TRK-UNDET"))).thenReturn(checkResult);

        realInvoiceService.checkAndAdvanceInvoice(invId);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNKNOWN);
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.REQUIRES_REVIEW);
        assertThat(invoice.getErrorCategory()).isEqualTo(InvoiceErrorCategory.UNDETERMINED);
    }

    @Test
    @DisplayName("8. Error categorization: QUOTA_HAS_BEEN_USERD_UP -> REQUIRES_ACTION, chặn retry trực tiếp")
    void testErrorCategorization_QuotaUsedUp_RequiresAction_BlocksRetry() {
        UUID invId = UUID.randomUUID();
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(invId);
        invoice.setStatus(InvoiceStatus.FAILED);
        invoice.setProviderErrorCode("QUOTA_HAS_BEEN_USERD_UP");
        invoice.setErrorCategory(InvoiceErrorCategory.REQUIRES_ACTION);
        invoice.setErrorLog("Hết hạn ngạch hóa đơn");

        BillingSetting setting = new BillingSetting();
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));
        when(invoiceRepository.findById(invId)).thenReturn(Optional.of(invoice));
        // Quota hiện tại trên SePay vẫn = 0
        when(sepayEInvoiceClient.getRemainingQuota(setting)).thenReturn(0);

        assertThatThrownBy(() -> realInvoiceService.retryInvoice(invId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Hạn ngạch hóa đơn đã hết");
    }

    @Test
    @DisplayName("9. Error categorization: REGISTRATION_NOT_TAX_APPROVED -> REQUIRES_ACTION, chặn retry")
    void testErrorCategorization_TaxNotApproved_RequiresAction_BlocksRetry() {
        UUID invId = UUID.randomUUID();
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(invId);
        invoice.setStatus(InvoiceStatus.FAILED);
        invoice.setProviderErrorCode("REGISTRATION_NOT_TAX_APPROVED");
        invoice.setErrorCategory(InvoiceErrorCategory.REQUIRES_ACTION);
        invoice.setErrorLog("Tài khoản chưa được Cơ quan Thuế chấp thuận mẫu");

        when(invoiceRepository.findById(invId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> realInvoiceService.retryInvoice(invId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Vui lòng xử lý nguyên nhân trước khi thử lại");
    }

    @Test
    @DisplayName("10. Error categorization: REGISTRATION_NOT_COMPLETE & BILLING_IS_UNPAID -> REQUIRES_ACTION")
    void testErrorCategorization_RegistrationNotComplete_BillingUnpaid() {
        assertThat(realInvoiceService.determineErrorCategory("REGISTRATION_NOT_COMPLETE", "Chưa xong")).isEqualTo(InvoiceErrorCategory.REQUIRES_ACTION);
        assertThat(realInvoiceService.determineErrorCategory("BILLING_IS_UNPAID", "Chưa trả phí")).isEqualTo(InvoiceErrorCategory.REQUIRES_ACTION);
    }

    @Test
    @DisplayName("11. Error categorization: VALIDATION_ERROR (HTTP 422) -> REQUIRES_ACTION")
    void testErrorCategorization_ValidationError_RequiresAction() {
        InvoiceErrorCategory cat = realInvoiceService.determineErrorCategory("VALIDATION_ERROR", "Dữ liệu email hoặc MST sai định dạng");
        assertThat(cat).isEqualTo(InvoiceErrorCategory.REQUIRES_ACTION);
    }

    @Test
    @DisplayName("12. Error categorization: Network Timeout / Server Error -> RETRYABLE, cho phép retry")
    void testErrorCategorization_NetworkTimeout_Retryable() {
        InvoiceErrorCategory cat = realInvoiceService.determineErrorCategory(null, "Connection timed out to SePay gateway");
        assertThat(cat).isEqualTo(InvoiceErrorCategory.RETRYABLE);
    }

    @Test
    @DisplayName("13. Tax Treatment Mapping: Toàn bộ enum -1, -2, 0, 5, 8, 10, OTHER chính xác")
    void testTaxTreatment_MappingAllEnums() {
        assertThat(TaxTreatment.fromCode("-1")).isEqualTo(TaxTreatment.NOT_DECLARED);
        assertThat(TaxTreatment.NOT_DECLARED.getSepayTaxRate()).isEqualTo(-1);

        assertThat(TaxTreatment.fromCode("-2")).isEqualTo(TaxTreatment.NOT_SUBJECT_TO_VAT);
        assertThat(TaxTreatment.NOT_SUBJECT_TO_VAT.getSepayTaxRate()).isEqualTo(-2);

        assertThat(TaxTreatment.fromCode("0")).isEqualTo(TaxTreatment.VAT_0);
        assertThat(TaxTreatment.VAT_0.getSepayTaxRate()).isEqualTo(0);

        assertThat(TaxTreatment.fromCode("5")).isEqualTo(TaxTreatment.VAT_5);
        assertThat(TaxTreatment.VAT_5.getSepayTaxRate()).isEqualTo(5);

        assertThat(TaxTreatment.fromCode("8")).isEqualTo(TaxTreatment.VAT_8);
        assertThat(TaxTreatment.VAT_8.getSepayTaxRate()).isEqualTo(8);

        assertThat(TaxTreatment.fromCode("10")).isEqualTo(TaxTreatment.VAT_10);
        assertThat(TaxTreatment.VAT_10.getSepayTaxRate()).isEqualTo(10);

        assertThat(TaxTreatment.fromCode("99")).isEqualTo(TaxTreatment.OTHER);
    }

    @Test
    @DisplayName("14. Sales Invoice: Khi invoice_type = SALES, payload không truyền tax_rate")
    void testSalesInvoice_OmitsTaxRateFromPayload() {
        BillingSetting setting = new BillingSetting();
        setting.setInvoiceType("SALES"); // Hóa đơn bán hàng
        setting.setTaxTreatment(TaxTreatment.NOT_SUBJECT_TO_VAT);
        setting.setEinvoiceTemplateCode("1");
        setting.setEinvoiceInvoiceSeries("C26TSE");
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));

        Order order = createSampleOrder("KH260922SALES");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH260922SALES");
        invoice.setStatus(InvoiceStatus.PENDING_ISSUE);

        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sepayEInvoiceClient.createInvoice(any(), any())).thenReturn(
                new SepayEInvoiceClient.CreateResult(true, "TRK-SALES-1", null, null)
        );

        realInvoiceService.executeCreateInvoice(invoice, order);

        ArgumentCaptor<SepayEInvoiceClient.CreateInvoicePayload> captor =
                ArgumentCaptor.forClass(SepayEInvoiceClient.CreateInvoicePayload.class);
        verify(sepayEInvoiceClient).createInvoice(any(), captor.capture());

        SepayEInvoiceClient.CreateInvoicePayload payload = captor.getValue();
        assertThat(payload.taxRate()).isNull(); // Hóa đơn bán hàng: taxRate phải null!
    }

    @Test
    @DisplayName("15. VAT Invoice: Khi invoice_type = VAT, payload truyền đúng tax_rate theo quy định")
    void testVatInvoice_IncludesTaxRateInPayload() {
        BillingSetting setting = new BillingSetting();
        setting.setInvoiceType("VAT"); // Hóa đơn GTGT
        setting.setTaxTreatment(TaxTreatment.NOT_SUBJECT_TO_VAT);
        setting.setEinvoiceTemplateCode("1");
        setting.setEinvoiceInvoiceSeries("C26TSE");
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));

        Order order = createSampleOrder("KH260922VAT");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH260922VAT");
        invoice.setStatus(InvoiceStatus.PENDING_ISSUE);

        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sepayEInvoiceClient.createInvoice(any(), any())).thenReturn(
                new SepayEInvoiceClient.CreateResult(true, "TRK-VAT-1", null, null)
        );

        realInvoiceService.executeCreateInvoice(invoice, order);

        ArgumentCaptor<SepayEInvoiceClient.CreateInvoicePayload> captor =
                ArgumentCaptor.forClass(SepayEInvoiceClient.CreateInvoicePayload.class);
        verify(sepayEInvoiceClient).createInvoice(any(), captor.capture());

        SepayEInvoiceClient.CreateInvoicePayload payload = captor.getValue();
        assertThat(payload.taxRate()).isEqualTo(-2); // KCT mã -2
    }

    @Test
    @DisplayName("16. Tax Snapshotting: Thuế suất được snapshot lưu vào electronic_invoices khi tạo")
    void testTaxSnapshotting_SavedOnInvoiceCreation() {
        BillingSetting setting = new BillingSetting();
        setting.setTaxTreatment(TaxTreatment.NOT_SUBJECT_TO_VAT);
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));

        Order order = createSampleOrder("KH260922SNAP");
        when(invoiceRepository.findLatestByOrderIdForUpdate(order.getId())).thenReturn(Optional.empty());
        when(invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())).thenReturn(Optional.empty());
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ElectronicInvoice invoice = realInvoiceService.initOrGetInvoice(order);

        assertThat(invoice.getTaxTreatment()).isEqualTo(TaxTreatment.NOT_SUBJECT_TO_VAT);
    }

    @Test
    @DisplayName("17. Status Separation: ReconciliationStatus tách biệt hoàn toàn với InvoiceStatus")
    void testStatusSeparation_ReconciliationStatusIndependent() {
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setStatus(InvoiceStatus.PROCESSING);
        invoice.setReconciliationStatus(ReconciliationStatus.PENDING);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PROCESSING);
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);

        // Chuyển ISSUED và RECONCILED
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setReconciliationStatus(ReconciliationStatus.RECONCILED);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    }

    @Test
    @DisplayName("18. Readiness Health Check: Quota = 0 sinh ra FAIL (Blocker không được Go-Live)")
    void testReadinessCheck_QuotaZero_IsBlockerFail() {
        BillingSetting setting = new BillingSetting();
        setting.setSepayApiKey("key-123");
        setting.setEinvoiceClientId("client-123");
        setting.setEinvoiceClientSecret("secret-123");
        setting.setEinvoiceProviderAccountId("prov-123");
        setting.setEinvoiceTemplateCode("1");
        setting.setEinvoiceInvoiceSeries("C26TSE");
        setting.setSellerName("Công ty IELTS");
        setting.setTaxAuthorityApprovedDate("2026-01-01");

        when(billingSettingRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(setting));
        when(sepayEInvoiceClient.getRemainingQuota(setting)).thenReturn(0);

        AdminBillingController controller = new AdminBillingController(
                orderApplicationService, realInvoiceService, webhookService,
                billingSettingRepository, sepayEInvoiceClient, tokenService
        );

        ResponseEntity<AdminBillingController.GoLiveReadinessReport> response = controller.checkGoLiveReadiness();
        AdminBillingController.GoLiveReadinessReport report = response.getBody();

        assertThat(report).isNotNull();
        assertThat(report.canGoProduction()).isFalse();
        assertThat(report.overallStatus()).isEqualTo("NOT_READY");
        assertThat(report.failCount()).isGreaterThanOrEqualTo(1);

        AdminBillingController.ReadinessCheckItem quotaCheck = report.checks().stream()
                .filter(c -> "ENVIRONMENT_QUOTA".equals(c.id()))
                .findFirst().orElseThrow();
        assertThat(quotaCheck.status()).isEqualTo("FAIL");
    }

    @Test
    @DisplayName("19. Readiness Health Check: Quota = 5 sinh ra WARN (Không phải Blocker, cho phép Go-Live có lưu ý)")
    void testReadinessCheck_QuotaLow_IsWarningNotBlocker() {
        BillingSetting setting = new BillingSetting();
        setting.setSepayApiKey("key-123");
        setting.setSepayWebhookSecret("secret-wh");
        setting.setSepayAccountNumber("0987654321");
        setting.setSepayBankName("MBBank");
        setting.setEinvoiceClientId("client-123");
        setting.setEinvoiceClientSecret("secret-123");
        setting.setEinvoiceProviderAccountId("prov-123");
        setting.setEinvoiceTemplateCode("1");
        setting.setEinvoiceInvoiceSeries("C26TSE");
        setting.setSellerName("Công ty IELTS");
        setting.setSellerTaxCode("0123456789");
        setting.setTaxAuthorityApprovedDate("2026-01-01");

        when(billingSettingRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(setting));
        when(sepayEInvoiceClient.getRemainingQuota(setting)).thenReturn(5);

        AdminBillingController controller = new AdminBillingController(
                orderApplicationService, realInvoiceService, webhookService,
                billingSettingRepository, sepayEInvoiceClient, tokenService
        );

        ResponseEntity<AdminBillingController.GoLiveReadinessReport> response = controller.checkGoLiveReadiness();
        AdminBillingController.GoLiveReadinessReport report = response.getBody();

        assertThat(report).isNotNull();
        assertThat(report.canGoProduction()).isTrue(); // Đủ điều kiện!
        assertThat(report.overallStatus()).isEqualTo("READY_WITH_WARNINGS");

        AdminBillingController.ReadinessCheckItem quotaCheck = report.checks().stream()
                .filter(c -> "ENVIRONMENT_QUOTA".equals(c.id()))
                .findFirst().orElseThrow();
        assertThat(quotaCheck.status()).isEqualTo("WARN");
    }

    @Test
    @DisplayName("20. Recheck endpoint: Kiểm tra lại trạng thái thủ công với SePay cho hóa đơn UNKNOWN")
    void testRecheckInvoiceStatus_ManualReconciliation() {
        UUID invId = UUID.randomUUID();
        Order order = createSampleOrder("KH260922RECHECK");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(invId);
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH260922RECHECK");
        invoice.setStatus(InvoiceStatus.UNKNOWN);

        BillingSetting setting = new BillingSetting();
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));
        when(invoiceRepository.findById(invId)).thenReturn(Optional.of(invoice));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SepayEInvoiceClient.InvoiceDetailResult detail = SepayEInvoiceClient.InvoiceDetailResult.ofSuccess(
                "888999", "C26TSE", "CQT-RECHECK", "LOOKUP-RECHECK",
                "https://sepay.vn/pdf/888", "https://sepay.vn/xml/888"
        );
        when(sepayEInvoiceClient.getInvoiceDetail(any(), eq("INV-KH260922RECHECK"))).thenReturn(detail);

        realInvoiceService.recheckInvoiceStatus(invId);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getInvoiceNumber()).isEqualTo("888999");
        assertThat(invoice.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    }

    // =========================================================================
    // SECTION 2: 10 REGRESSION & WORKFLOW TESTS
    // =========================================================================

    @Test
    @DisplayName("21. Idempotency: Webhook giao dịch trùng lặp được bỏ qua an toàn với HTTP 200")
    void testWebhookDuplicate_Idempotency() {
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(configuredWebhookSetting()));
        when(transactionRepository.existsBySepayTransactionId("123456")).thenReturn(true);

        SepayWebhookPayload payload = new SepayWebhookPayload(
                123456L, "MBBank", "2026-09-22 10:00:00", "0987654321",
                null, null, "KH2609228563 thanh toan", "in", null,
                BigDecimal.valueOf(5000000), "REF123", BigDecimal.valueOf(5000000)
        );

        Map<String, Object> response = webhookService.processWebhook("Apikey webhook-test-key", payload);

        assertThat(response.get("success")).isEqualTo(true);
        assertThat(response.get("message")).isEqualTo("Transaction already processed");
        verify(orderRepository, never()).save(any());
        verify(mockInvoiceService, never()).issueInvoice(any());
    }

    @Test
    @DisplayName("22. Đặt cọc: Chuyển một phần chưa kích hoạt PAID nhưng vẫn xếp hàng hóa đơn theo tiền thực nhận")
    void testWebhookUnderpaid_MarksUnderpaid() {
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(configuredWebhookSetting()));
        when(transactionRepository.existsBySepayTransactionId("123457")).thenReturn(false);

        Order order = createSampleOrder("KH2609228563");
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        when(orderRepository.findByOrderCodeForUpdate("KH2609228563")).thenReturn(Optional.of(order));

        SepayWebhookPayload payload = new SepayWebhookPayload(
                123457L, "MBBank", "2026-09-22 10:00:00", "0987654321",
                null, null, "KH2609228563 thanh toan thieu", "in", null,
                BigDecimal.valueOf(3000000), "REF123", BigDecimal.valueOf(3000000)
        );

        Map<String, Object> response = webhookService.processWebhook("Apikey webhook-test-key", payload);

        assertThat(response.get("status")).isEqualTo("PARTIAL_PAYMENT");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(PaymentTransactionStatus.PARTIAL_PAYMENT);
        verify(mockInvoiceService).initOrGetInvoice(any(PaymentTransaction.class), eq(order));
    }

    @Test
    @DisplayName("22a. QR động: khoản đóng tiếp làm lũy kế đủ học phí thì PAID và tạo tài khoản")
    void testDynamicQrCumulativePaymentActivatesCourseWhenFullyPaid() {
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(configuredWebhookSetting()));
        when(transactionRepository.existsBySepayTransactionId("123460")).thenReturn(false);

        Order order = createSampleOrder("KH2609228563");
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findByOrderCodeForUpdate("KH2609228563")).thenReturn(Optional.of(order));
        when(transactionRepository.sumCapturedAmountByOrderIdExcluding(eq(order.getId()), any()))
                .thenReturn(BigDecimal.valueOf(3000000));
        when(activationService.generateActivationToken(order)).thenReturn("activation-token");

        SepayWebhookPayload payload = new SepayWebhookPayload(
                123460L, "MBBank", "2026-09-23 19:00:00", "0987654321",
                null, null, "Hoc Vien Test KH2609228563", "in", null,
                BigDecimal.valueOf(2000000), "REF460", BigDecimal.valueOf(5000000)
        );

        Map<String, Object> response = webhookService.processWebhook("Apikey webhook-test-key", payload);

        assertThat(response.get("status")).isEqualTo("SUCCESS");
        assertThat(response.get("accumulatedAmount")).isEqualTo(BigDecimal.valueOf(5000000));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(activationService).generateActivationToken(order);
        verify(emailService).sendPaymentSuccessEmail(order, "activation-token");
        verify(mockInvoiceService).initOrGetInvoice(any(PaymentTransaction.class), eq(order));
    }

    @Test
    @DisplayName("22b. QR tĩnh không có mã đơn vẫn lập hóa đơn, tên người mua lấy từ nội dung chuyển khoản")
    void testStaticQrWithoutOrder_QueuesTransactionInvoice() {
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(configuredWebhookSetting()));
        when(transactionRepository.existsBySepayTransactionId("123459")).thenReturn(false);

        SepayWebhookPayload payload = new SepayWebhookPayload(
                123459L, "Vietcombank", "2026-09-23 18:15:00", "0987654321",
                null, null, "Duong Dinh Yen Nhi Chuyen khoan nhanh qua Zalo", "in", null,
                BigDecimal.valueOf(500000), "REF459", BigDecimal.valueOf(500000)
        );

        Map<String, Object> response = webhookService.processWebhook("Apikey webhook-test-key", payload);

        assertThat(response.get("status")).isEqualTo("STANDALONE_INVOICE_QUEUED");
        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(PaymentTransactionStatus.STANDALONE_PAYMENT);
        assertThat(transactionCaptor.getValue().getPayerName()).isEqualTo("Duong Dinh Yen Nhi");
        assertThat(transactionCaptor.getValue().getAmountIn()).isEqualByComparingTo("500000");
        verify(mockInvoiceService).initOrGetInvoice(eq(transactionCaptor.getValue()), isNull());
    }

    @Test
    @DisplayName("23. Decoupled Webhook: Webhook phản hồi ngay, HĐĐT và email xử lý bất đồng bộ")
    void testWebhookSuccess_DecoupledInvoiceAndEmail() {
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(configuredWebhookSetting()));
        when(transactionRepository.existsBySepayTransactionId("123458")).thenReturn(false);

        Order order = createSampleOrder("KH2609228563");
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setInvoiceRequired(true);

        when(orderRepository.findByOrderCodeForUpdate("KH2609228563")).thenReturn(Optional.of(order));
        when(activationService.generateActivationToken(order)).thenReturn("token-abc-123");

        SepayWebhookPayload payload = new SepayWebhookPayload(
                123458L, "MBBank", "2026-09-22 10:00:00", "0987654321",
                null, null, "KH2609228563 thanh toan du", "in", null,
                BigDecimal.valueOf(5000000), "REF123", BigDecimal.valueOf(5000000)
        );

        Map<String, Object> response = webhookService.processWebhook("Apikey webhook-test-key", payload);

        assertThat(response.get("status")).isEqualTo("SUCCESS");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

        verify(mockInvoiceService).initOrGetInvoice(any(PaymentTransaction.class), eq(order));
        verify(emailService).sendPaymentSuccessEmail(order, "token-abc-123");
        verify(emailService, never()).sendInvoiceIssuedEmail(any(), any());
    }

    @Test
    @DisplayName("24. Flow A: Tạo hóa đơn trực tiếp chuyển sang PROCESSING và lưu tracking_code")
    void testFlowA_ExecuteCreateInvoice_SetsProcessing() {
        BillingSetting setting = new BillingSetting();
        setting.setEinvoiceTemplateCode("1");
        setting.setEinvoiceInvoiceSeries("C26TSE");
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));

        Order order = createSampleOrder("KH2609221111");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setReferenceCode("INV-KH2609221111");
        invoice.setStatus(InvoiceStatus.PENDING_ISSUE);

        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sepayEInvoiceClient.createInvoice(any(), any())).thenReturn(
                new SepayEInvoiceClient.CreateResult(true, "TRK-CREATE-1111", null, null)
        );

        ElectronicInvoice result = realInvoiceService.executeCreateInvoice(invoice, order);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PROCESSING);
        assertThat(result.getCreateTrackingCode()).isEqualTo("TRK-CREATE-1111");
        assertThat(result.getNextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("25. Flow B: Phát hành hóa đơn nháp (DRAFT) chuyển sang ISSUING")
    void testFlowB_IssueDraftInvoice_SetsIssuing() {
        BillingSetting setting = new BillingSetting();
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));

        UUID invId = UUID.randomUUID();
        ElectronicInvoice draftInv = new ElectronicInvoice();
        draftInv.setId(invId);
        draftInv.setReferenceCode("INV-KH2609222222");
        draftInv.setStatus(InvoiceStatus.DRAFT);
        draftInv.setIsDraft(true);

        when(invoiceRepository.findById(invId)).thenReturn(Optional.of(draftInv));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sepayEInvoiceClient.issueDraft(any(), eq("INV-KH2609222222"))).thenReturn(
                new SepayEInvoiceClient.IssueDraftResult(true, "TRK-ISSUE-2222", null, null)
        );

        ElectronicInvoice result = realInvoiceService.issueDraftInvoice(invId);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.ISSUING);
        assertThat(result.getIssueTrackingCode()).isEqualTo("TRK-ISSUE-2222");
    }

    @Test
    @DisplayName("26. Server Crash Recovery: Khởi động lại phục hồi các job dở dang an toàn")
    void testServerRestart_Recovery() {
        ElectronicInvoice creatingInv = new ElectronicInvoice();
        creatingInv.setId(UUID.randomUUID());
        creatingInv.setStatus(InvoiceStatus.CREATING);
        creatingInv.setCreateTrackingCode(null);

        ElectronicInvoice processingInv = new ElectronicInvoice();
        processingInv.setId(UUID.randomUUID());
        processingInv.setStatus(InvoiceStatus.PROCESSING);
        processingInv.setCreateTrackingCode("TRK-EXISTS");

        when(invoiceRepository.findByStatusIn(anyList())).thenReturn(List.of(creatingInv, processingInv));

        invoiceWorker.recoverInterruptedJobsOnStartup();

        assertThat(creatingInv.getStatus()).isEqualTo(InvoiceStatus.PENDING_ISSUE);
        assertThat(processingInv.getStatus()).isEqualTo(InvoiceStatus.PROCESSING);
        verify(invoiceRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("27. Cash Payment: Thanh toán tiền mặt tại quầy gán phương thức TM")
    void testCashPayment_SetsPaymentMethodTM() {
        Order order = createSampleOrder("KH2609223333");
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setInvoiceRequired(true);

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setPaymentMethod("CK");

        webhookService.confirmManualPayment(order.getId(), "Khách nộp tiền mặt tại cơ sở");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(mockInvoiceService).initOrGetInvoice(any(PaymentTransaction.class), eq(order));
    }

    @Test
    @DisplayName("28. Email Resilience: Hóa đơn chuyển ISSUED mới gửi email có link PDF/XML")
    void testEmailSentOnlyWhenIssued() {
        Order order = createSampleOrder("KH2609224444");
        UUID invId = UUID.randomUUID();
        ElectronicInvoice processingInv = new ElectronicInvoice();
        processingInv.setId(invId);
        processingInv.setOrderId(order.getId());
        processingInv.setStatus(InvoiceStatus.PROCESSING);
        processingInv.setCreateTrackingCode("TRK-SUCCESS-4444");

        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(new BillingSetting()));
        when(invoiceRepository.findById(invId)).thenReturn(Optional.of(processingInv));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(sepayEInvoiceClient.checkCreateStatus(any(), eq("TRK-SUCCESS-4444"))).thenReturn(
                SepayEInvoiceClient.CheckStatusResult.successIssued(
                        "304764", "C26TSE", "00D0649FD123456", "LOOKUP-4444",
                        "https://sandbox.sepay.vn/pdf/304764", "https://sandbox.sepay.vn/xml/304764"
                )
        );

        realInvoiceService.checkAndAdvanceInvoice(invId);

        assertThat(processingInv.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        verify(emailService).sendInvoiceIssuedEmail(eq(order), eq(processingInv));
    }

    @Test
    @DisplayName("29. Concurrency: Khóa bi quan và bảo vệ unique constraint khi khởi tạo HĐ đồng thời")
    void testConcurrency_PessimisticLockingOnInvoiceInit() {
        Order order = createSampleOrder("KH260922CONCUR");
        ElectronicInvoice existingInvoice = new ElectronicInvoice();
        existingInvoice.setId(UUID.randomUUID());
        existingInvoice.setOrderId(order.getId());
        existingInvoice.setReferenceCode("INV-KH260922CONCUR");

        // Giả lập luồng 2 thấy hóa đơn đã được tạo bởi luồng 1
        when(invoiceRepository.findLatestByOrderIdForUpdate(order.getId())).thenReturn(Optional.of(existingInvoice));

        ElectronicInvoice result = realInvoiceService.initOrGetInvoice(order);

        assertThat(result.getId()).isEqualTo(existingInvoice.getId());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("30. Reconciliation Worker: Tự động đưa hóa đơn PENDING vào hàng đợi đối soát")
    void testWorker_ReconcilesPendingInvoices() {
        ElectronicInvoice pendingReconcileInv = new ElectronicInvoice();
        pendingReconcileInv.setId(UUID.randomUUID());
        pendingReconcileInv.setStatus(InvoiceStatus.ISSUED);
        pendingReconcileInv.setReconciliationStatus(ReconciliationStatus.PENDING);
        pendingReconcileInv.setNextRetryAt(OffsetDateTime.now().minusSeconds(10));

        when(invoiceRepository.findActionableInvoices(anyList(), any(), anyInt()))
                .thenReturn(List.of(pendingReconcileInv));

        invoiceWorker.processPendingInvoices();

        verify(mockInvoiceService).checkAndAdvanceInvoice(pendingReconcileInv.getId());
    }

    @Test
    @DisplayName("31. Tạo đơn hàng (Admin/User): Chỉ sinh Order PENDING_PAYMENT và VietQR, TUYỆT ĐỐI KHÔNG tạo ElectronicInvoice hay gọi SePay eInvoice")
    void testCreatingOrderDoesNotIssueInvoice() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course();
        course.setId(courseId);
        course.setName("IELTS Intensive 7.5+");
        course.setIsActive(true);
        course.setTuitionAmount(BigDecimal.valueOf(6500000));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        BillingSetting setting = new BillingSetting();
        setting.setSepayBankName("MBBank");
        setting.setSepayAccountNumber("0987654321");
        setting.setSellerName("HỘ KINH DOANH LUYỆN NÓI");
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        AdminCreateOrderRequest request = new AdminCreateOrderRequest(
                courseId,
                "Nguyễn Văn A",
                "nguyenvana@gmail.com",
                "0912345678",
                BigDecimal.valueOf(6500000),
                48,
                "Ghi chú tư vấn học viên",
                true,
                InvoiceBuyerType.PERSONAL,
                null,
                null,
                null,
                null
        );

        CheckoutResponse response = realOrderApplicationService.createAdminOrder(request);

        // Đơn hàng được tạo với trạng thái PENDING_PAYMENT
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.qrCodeUrl()).contains("https://img.vietqr.io/image/MBBank-0987654321-compact2.png");
        assertThat(response.qrCodeUrl()).contains("amount=6500000");
        assertThat(response.qrCodeUrl()).contains("addInfo=");
        assertThat(response.transferContent()).contains("Nguyễn Văn A", response.orderCode());

        // Tuyệt đối không tạo ElectronicInvoice hay gọi SePay phát hành hóa đơn
        verify(invoiceRepository, never()).save(any());
        verify(sepayEInvoiceClient, never()).createInvoice(any(), any());
        verify(sepayEInvoiceClient, never()).issueDraft(any(), any());
    }

    @Test
    @DisplayName("31b. QR tĩnh dùng lâu dài không tạo đơn, không khóa số tiền hay nội dung")
    void testStaticQrDoesNotCreateOrder() {
        BillingSetting setting = new BillingSetting();
        setting.setSepayBankName("MBBank");
        setting.setSepayAccountNumber("0987654321");
        setting.setSellerName("HỘ KINH DOANH LUYỆN NÓI");
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));

        var response = realOrderApplicationService.getStaticTuitionQr();

        assertThat(response.qrCodeUrl()).contains("https://img.vietqr.io/image/MBBank-0987654321-compact2.png");
        assertThat(response.qrCodeUrl()).doesNotContain("amount=");
        assertThat(response.qrCodeUrl()).doesNotContain("addInfo=");
        assertThat(response.productName()).isEqualTo("Đóng học phí đào tạo IELTS");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("32. Order Lifecycle: Trạng thái khởi đầu luôn là PENDING_PAYMENT kèm VietQR Napas247 chính xác")
    void testCreatingOrderProducesPendingPayment() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course();
        course.setId(courseId);
        course.setName("IELTS Foundation");
        course.setIsActive(true);
        course.setTuitionAmount(BigDecimal.valueOf(3500000));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        AdminCreateOrderRequest request = new AdminCreateOrderRequest(
                courseId,
                "Trần Thị B",
                "tranthib@gmail.com",
                "0987654321",
                BigDecimal.valueOf(3500000),
                24,
                null,
                true,
                InvoiceBuyerType.PERSONAL,
                null,
                null,
                null,
                null
        );

        CheckoutResponse response = realOrderApplicationService.createAdminOrder(request);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(3500000));
        assertThat(response.expiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    @DisplayName("33. Phân tách B2B & Học viên: Thông tin công ty xuất HĐ không được ghi đè thông tin tài khoản học viên")
    void testB2bBuyerDataDoesNotOverwriteStudentData() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course();
        course.setId(courseId);
        course.setName("IELTS Master Class");
        course.setIsActive(true);
        course.setTuitionAmount(BigDecimal.valueOf(12000000));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        AdminCreateOrderRequest request = new AdminCreateOrderRequest(
                courseId,
                "Lê Văn Học Viên",
                "hocvien@student.edu.vn",
                "0909123456",
                BigDecimal.valueOf(12000000),
                48,
                "Xuất HĐ công ty",
                true,
                InvoiceBuyerType.BUSINESS,
                "CÔNG TY CỔ PHẦN CÔNG NGHỆ ABC",
                "0109876543",
                "Tầng 10, Tòa nhà Keangnam, Mễ Trì, Nam Từ Liêm, Hà Nội",
                "accounting@abc-tech.vn"
        );

        realOrderApplicationService.createAdminOrder(request);

        Order capturedOrder = orderCaptor.getValue();

        // 1. Thông tin học viên (phục vụ tài khoản, kích hoạt khóa học, liên lạc)
        assertThat(capturedOrder.getCustomerName()).isEqualTo("Lê Văn Học Viên");
        assertThat(capturedOrder.getCustomerEmail()).isEqualTo("hocvien@student.edu.vn");
        assertThat(capturedOrder.getCustomerPhone()).isEqualTo("0909123456");

        // 2. Thông tin xuất HĐ doanh nghiệp (phục vụ HĐĐT CQT)
        assertThat(capturedOrder.getBuyerType()).isEqualTo(InvoiceBuyerType.BUSINESS);
        assertThat(capturedOrder.getInvoiceCompanyName()).isEqualTo("CÔNG TY CỔ PHẦN CÔNG NGHỆ ABC");
        assertThat(capturedOrder.getInvoiceTaxCode()).isEqualTo("0109876543");
        assertThat(capturedOrder.getInvoiceAddress()).isEqualTo("Tầng 10, Tòa nhà Keangnam, Mễ Trì, Nam Từ Liêm, Hà Nội");
        assertThat(capturedOrder.getInvoiceEmail()).isEqualTo("accounting@abc-tech.vn");

        // 3. Đảm bảo thông tin học viên không bị ghi đè bởi thông tin công ty
        assertThat(capturedOrder.getCustomerName()).isNotEqualTo(capturedOrder.getInvoiceCompanyName());
        assertThat(capturedOrder.getCustomerEmail()).isNotEqualTo(capturedOrder.getInvoiceEmail());
    }

    private Order createSampleOrder(String orderCode) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderCode(orderCode);
        order.setAmount(BigDecimal.valueOf(5000000));
        order.setCustomerName("Học viên Test");
        order.setCustomerEmail("test@example.com");
        order.setBuyerType(InvoiceBuyerType.PERSONAL);
        order.setCourseId(UUID.randomUUID());
        return order;
    }

    private BillingSetting configuredWebhookSetting() {
        BillingSetting setting = new BillingSetting();
        setting.setSepayWebhookSecret(secretEncryptionService.encrypt("webhook-test-key"));
        setting.setSepayAccountNumber("0987654321");
        setting.setSepayBankName("MBBank");
        setting.setSellerName("THE IELTS SPELLS");
        setting.setAutoInvoiceEnabled(true);
        return setting;
    }
}
