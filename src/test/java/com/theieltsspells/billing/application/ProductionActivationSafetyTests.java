package com.theieltsspells.billing.application;

import com.theieltsspells.academic.application.EnrollmentApplicationService;
import com.theieltsspells.billing.domain.*;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.billing.infrastructure.persistence.PaymentTransactionRepository;
import com.theieltsspells.billing.infrastructure.persistence.PilotExecutionRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductionActivationSafetyTests {

    @Mock
    private BillingSettingRepository billingSettingRepository;

    @Mock
    private PilotExecutionRepository pilotExecutionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ElectronicInvoiceRepository invoiceRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private EnrollmentApplicationService enrollmentService;

    private ProductionActivationService activationService;

    @BeforeEach
    void setUp() {
        activationService = new ProductionActivationService(
                billingSettingRepository,
                pilotExecutionRepository,
                orderRepository,
                invoiceRepository,
                paymentTransactionRepository,
                enrollmentService
        );
    }

    private Order createSampleOrder(UUID orderId, OrderStatus status) {
        Order order = new Order();
        order.setId(orderId);
        order.setOrderCode("ORD-PILOT-001");
        order.setStatus(status);
        order.setAmount(new BigDecimal("1500000"));
        order.setUserId(UUID.randomUUID());
        order.setCourseId(UUID.randomUUID());
        return order;
    }

    private PaymentTransaction createSamplePaymentTransaction(UUID orderId, PaymentTransactionStatus status, String sepayTxId) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID());
        tx.setOrderId(orderId);
        tx.setOrderCode("ORD-PILOT-001");
        tx.setStatus(status);
        tx.setSepayTransactionId(sepayTxId);
        tx.setAmountIn(new BigDecimal("1500000"));
        return tx;
    }

    private ElectronicInvoice createSampleInvoice(UUID orderId, InvoiceStatus status, ReconciliationStatus recStatus) {
        ElectronicInvoice inv = new ElectronicInvoice();
        inv.setId(UUID.randomUUID());
        inv.setOrderId(orderId);
        inv.setStatus(status);
        inv.setReconciliationStatus(recStatus);
        inv.setReferenceCode("REF-2026-PILOT-001");
        inv.setInvoiceNumber("0000001");
        inv.setPdfUrl("https://einvoice.example.com/inv.pdf");
        inv.setXmlUrl("https://einvoice.example.com/inv.xml");
        inv.setBuyerEmail("student@example.com");
        return inv;
    }

    // =========================================================================
    // MANDATORY AUDIT TESTS: PILOT EVALUATION MILESTONES (ALL 7 GATES)
    // =========================================================================

    @Test
    @DisplayName("cannotActivateProductionIfPaymentNotSuccessful: Pilot evaluation fails when payment is not SUCCESS")
    void cannotActivateProductionIfPaymentNotSuccessful() {
        UUID orderId = UUID.randomUUID();
        Order order = createSampleOrder(orderId, OrderStatus.PAID);
        PaymentTransaction failedTx = createSamplePaymentTransaction(orderId, PaymentTransactionStatus.UNDERPAID, null);
        ElectronicInvoice inv = createSampleInvoice(orderId, InvoiceStatus.ISSUED, ReconciliationStatus.RECONCILED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(failedTx));
        when(enrollmentService.isStudentEnrolledActive(order.getCourseId(), order.getUserId())).thenReturn(true);
        when(invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(inv));
        when(pilotExecutionRepository.save(any(PilotExecution.class))).thenAnswer(i -> i.getArgument(0));

        PilotExecution result = activationService.evaluatePilotOrder(orderId, "admin");

        assertThat(result.getStatus()).isEqualTo(PilotResultStatus.FAILED);
        assertThat(result.isPaymentSuccess()).isFalse();
        assertThat(result.getFailureReason()).contains("Giao dịch thanh toán SePay chưa thành công");
    }

    @Test
    @DisplayName("cannotActivateProductionIfEnrollmentNotActive: Pilot evaluation fails when enrollment is not ACTIVE")
    void cannotActivateProductionIfEnrollmentNotActive() {
        UUID orderId = UUID.randomUUID();
        Order order = createSampleOrder(orderId, OrderStatus.PAID);
        PaymentTransaction tx = createSamplePaymentTransaction(orderId, PaymentTransactionStatus.SUCCESS, "TX-SEPAY-999");
        ElectronicInvoice inv = createSampleInvoice(orderId, InvoiceStatus.ISSUED, ReconciliationStatus.RECONCILED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(tx));
        when(enrollmentService.isStudentEnrolledActive(order.getCourseId(), order.getUserId())).thenReturn(false);
        when(invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(inv));
        when(pilotExecutionRepository.save(any(PilotExecution.class))).thenAnswer(i -> i.getArgument(0));

        PilotExecution result = activationService.evaluatePilotOrder(orderId, "admin");

        assertThat(result.getStatus()).isEqualTo(PilotResultStatus.FAILED);
        assertThat(result.isEnrollmentActive()).isFalse();
        assertThat(result.getFailureReason()).contains("Ghi danh học viên chưa được kích hoạt");
    }

    @Test
    @DisplayName("cannotActivateProductionIfInvoiceNotIssued: Pilot evaluation fails when invoice is not ISSUED")
    void cannotActivateProductionIfInvoiceNotIssued() {
        UUID orderId = UUID.randomUUID();
        Order order = createSampleOrder(orderId, OrderStatus.PAID);
        PaymentTransaction tx = createSamplePaymentTransaction(orderId, PaymentTransactionStatus.SUCCESS, "TX-SEPAY-999");
        ElectronicInvoice inv = createSampleInvoice(orderId, InvoiceStatus.FAILED, ReconciliationStatus.RECONCILED);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(tx));
        when(enrollmentService.isStudentEnrolledActive(order.getCourseId(), order.getUserId())).thenReturn(true);
        when(invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(inv));
        when(pilotExecutionRepository.save(any(PilotExecution.class))).thenAnswer(i -> i.getArgument(0));

        PilotExecution result = activationService.evaluatePilotOrder(orderId, "admin");

        assertThat(result.getStatus()).isEqualTo(PilotResultStatus.FAILED);
        assertThat(result.isInvoiceIssued()).isFalse();
        assertThat(result.getFailureReason()).contains("Hóa đơn điện tử chưa ở trạng thái ISSUED");
    }

    @Test
    @DisplayName("cannotActivateProductionIfInvoiceNotReconciled: Pilot evaluation fails when invoice is not RECONCILED")
    void cannotActivateProductionIfInvoiceNotReconciled() {
        UUID orderId = UUID.randomUUID();
        Order order = createSampleOrder(orderId, OrderStatus.PAID);
        PaymentTransaction tx = createSamplePaymentTransaction(orderId, PaymentTransactionStatus.SUCCESS, "TX-SEPAY-999");
        ElectronicInvoice inv = createSampleInvoice(orderId, InvoiceStatus.ISSUED, ReconciliationStatus.REQUIRES_REVIEW);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(tx));
        when(enrollmentService.isStudentEnrolledActive(order.getCourseId(), order.getUserId())).thenReturn(true);
        when(invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(inv));
        when(pilotExecutionRepository.save(any(PilotExecution.class))).thenAnswer(i -> i.getArgument(0));

        PilotExecution result = activationService.evaluatePilotOrder(orderId, "admin");

        assertThat(result.getStatus()).isEqualTo(PilotResultStatus.FAILED);
        assertThat(result.isInvoiceReconciled()).isFalse();
        assertThat(result.getFailureReason()).contains("Hóa đơn chưa được đối soát thành công");
    }

    // =========================================================================
    // MANDATORY AUDIT TESTS: PRODUCTION TRANSITION GATING
    // =========================================================================

    @Test
    @DisplayName("cannotActivateProductionWithoutPilot: Transition blocks when no pilot has been executed")
    void cannotActivateProductionWithoutPilot() {
        BillingSetting setting = new BillingSetting();
        setting.setActivationState(ProductionActivationState.PRODUCTION_PILOT);

        when(billingSettingRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(setting));
        when(pilotExecutionRepository.findFirstByOrderByEvaluatedAtDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activationService.transitionActivationState(
                ProductionActivationState.PRODUCTION_ACTIVE, true, false, "admin"
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("chưa thực hiện và vượt qua đợt thử nghiệm Pilot nào");
    }

    @Test
    @DisplayName("cannotActivateProductionWithPilotInProgress: Transition blocks when pilot is IN_PROGRESS")
    void cannotActivateProductionWithPilotInProgress() {
        BillingSetting setting = new BillingSetting();
        setting.setActivationState(ProductionActivationState.PRODUCTION_PILOT);

        PilotExecution pilot = new PilotExecution();
        pilot.setStatus(PilotResultStatus.IN_PROGRESS);

        when(billingSettingRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(setting));
        when(pilotExecutionRepository.findFirstByOrderByEvaluatedAtDesc()).thenReturn(Optional.of(pilot));

        assertThatThrownBy(() -> activationService.transitionActivationState(
                ProductionActivationState.PRODUCTION_ACTIVE, true, false, "admin"
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vẫn đang xử lý (PilotResult = IN_PROGRESS)");
    }

    @Test
    @DisplayName("cannotActivateProductionWithFailedPilot: Transition blocks when pilot has FAILED status")
    void cannotActivateProductionWithFailedPilot() {
        BillingSetting setting = new BillingSetting();
        setting.setActivationState(ProductionActivationState.PRODUCTION_PILOT);

        PilotExecution pilot = new PilotExecution();
        pilot.setStatus(PilotResultStatus.FAILED);
        pilot.setFailureReason("Lỗi cấp số hóa đơn từ cơ quan thuế");

        when(billingSettingRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(setting));
        when(pilotExecutionRepository.findFirstByOrderByEvaluatedAtDesc()).thenReturn(Optional.of(pilot));

        assertThatThrownBy(() -> activationService.transitionActivationState(
                ProductionActivationState.PRODUCTION_ACTIVE, true, false, "admin"
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("đợt thử nghiệm Pilot gần nhất bị thất bại");
    }

    @Test
    @DisplayName("passedPilotStillRequiresExplicitAdminConfirmation: Even with PASSED pilot, explicitAdminConfirmation is required")
    void passedPilotStillRequiresExplicitAdminConfirmation() {
        BillingSetting setting = new BillingSetting();
        setting.setActivationState(ProductionActivationState.PRODUCTION_PILOT);

        when(billingSettingRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(setting));

        // When explicitAdminConfirmation == false
        assertThatThrownBy(() -> activationService.transitionActivationState(
                ProductionActivationState.PRODUCTION_ACTIVE, false, false, "admin"
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("xác nhận tường minh của Quản trị viên");
    }

    @Test
    @DisplayName("passedPilotAllowsProductionActivationAfterAdminConfirmation: PASSED pilot with explicit confirmation successfully transitions to PRODUCTION_ACTIVE")
    void passedPilotAllowsProductionActivationAfterAdminConfirmation() {
        BillingSetting setting = new BillingSetting();
        setting.setActivationState(ProductionActivationState.PRODUCTION_PILOT);

        PilotExecution pilot = new PilotExecution();
        pilot.setId(UUID.randomUUID());
        pilot.setStatus(PilotResultStatus.PASSED);
        pilot.setEvaluatedAt(OffsetDateTime.now());

        when(billingSettingRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(setting));
        when(pilotExecutionRepository.findFirstByOrderByEvaluatedAtDesc()).thenReturn(Optional.of(pilot));
        when(billingSettingRepository.save(any(BillingSetting.class))).thenAnswer(i -> i.getArgument(0));

        BillingSetting result = activationService.transitionActivationState(
                ProductionActivationState.PRODUCTION_ACTIVE, true, false, "admin"
        );

        assertThat(result.getActivationState()).isEqualTo(ProductionActivationState.PRODUCTION_ACTIVE);
        assertThat(result.getIsSandbox()).isFalse();
    }
}
