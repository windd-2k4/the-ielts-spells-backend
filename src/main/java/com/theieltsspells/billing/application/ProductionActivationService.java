package com.theieltsspells.billing.application;

import com.theieltsspells.academic.application.EnrollmentApplicationService;
import com.theieltsspells.billing.domain.*;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.billing.infrastructure.persistence.PaymentTransactionRepository;
import com.theieltsspells.billing.infrastructure.persistence.PilotExecutionRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ProductionActivationService {

    private final BillingSettingRepository billingSettingRepository;
    private final PilotExecutionRepository pilotExecutionRepository;
    private final OrderRepository orderRepository;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EnrollmentApplicationService enrollmentService;

    @Autowired
    public ProductionActivationService(
            BillingSettingRepository billingSettingRepository,
            PilotExecutionRepository pilotExecutionRepository,
            OrderRepository orderRepository,
            ElectronicInvoiceRepository invoiceRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            EnrollmentApplicationService enrollmentService
    ) {
        this.billingSettingRepository = billingSettingRepository;
        this.pilotExecutionRepository = pilotExecutionRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.enrollmentService = enrollmentService;
    }

    public ProductionActivationService(
            BillingSettingRepository billingSettingRepository,
            PilotExecutionRepository pilotExecutionRepository,
            OrderRepository orderRepository,
            ElectronicInvoiceRepository invoiceRepository,
            PaymentTransactionRepository paymentTransactionRepository
    ) {
        this(billingSettingRepository, pilotExecutionRepository, orderRepository, invoiceRepository, paymentTransactionRepository, null);
    }

    /**
     * Evaluates all 7 operational milestones of an order participating in a controlled Pilot run.
     * Persists the evaluation result as an immutable PilotExecution audit record.
     */
    @Transactional
    public PilotExecution evaluatePilotOrder(UUID orderId, String evaluatedBy) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));

        PilotExecution execution = new PilotExecution();
        execution.setOrderId(order.getId());
        execution.setOrderCode(order.getOrderCode());
        execution.setEvaluatedAt(OffsetDateTime.now());
        execution.setEvaluatedBy(evaluatedBy != null ? evaluatedBy : "SYSTEM");

        StringBuilder failureReasons = new StringBuilder();

        // 1. Order status is PAID
        boolean orderPaid = (order.getStatus() == OrderStatus.PAID);
        execution.setOrderPaid(orderPaid);
        if (!orderPaid) {
            failureReasons.append("Đơn hàng chưa ở trạng thái PAID (hiện tại: ").append(order.getStatus()).append("). ");
        }

        // 2. Payment is SUCCESS and transaction ID exists
        Optional<PaymentTransaction> txOpt = paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId());
        if (txOpt.isEmpty() && order.getOrderCode() != null) {
            txOpt = paymentTransactionRepository.findFirstByOrderCodeOrderByCreatedAtDesc(order.getOrderCode());
        }

        boolean paymentSuccess = txOpt.isPresent()
                && txOpt.get().getStatus() == PaymentTransactionStatus.SUCCESS
                && txOpt.get().getSepayTransactionId() != null
                && !txOpt.get().getSepayTransactionId().isBlank();
        execution.setPaymentSuccess(paymentSuccess);
        txOpt.ifPresent(tx -> execution.setPaymentTransactionId(tx.getSepayTransactionId()));
        if (!paymentSuccess) {
            failureReasons.append("Giao dịch thanh toán SePay chưa thành công hoặc thiếu sepayTransactionId. ");
        }

        // 3. Student Enrollment is ACTIVE
        boolean enrollmentActive = false;
        if (order.getUserId() != null && enrollmentService != null) {
            enrollmentActive = enrollmentService.isStudentEnrolledActive(order.getCourseId(), order.getUserId());
        } else if (order.getUserId() == null) {
            // Guest checkout: verification token generated / access granted
            enrollmentActive = orderPaid; // Guest order activated upon payment
        } else {
            enrollmentActive = orderPaid;
        }
        execution.setEnrollmentActive(enrollmentActive);
        if (!enrollmentActive) {
            failureReasons.append("Ghi danh học viên chưa được kích hoạt ở trạng thái ACTIVE. ");
        }

        // 4. Electronic Invoice is ISSUED, reference_code and invoice_number exist, reconciliationStatus is RECONCILED
        Optional<ElectronicInvoice> invOpt = invoiceRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId());
        boolean invoiceIssued = false;
        boolean invoiceReconciled = false;

        if (invOpt.isEmpty()) {
            failureReasons.append("Chưa tìm thấy bản ghi Hóa đơn điện tử nào gắn với đơn hàng này. ");
        } else {
            ElectronicInvoice inv = invOpt.get();
            invoiceIssued = (inv.getStatus() == InvoiceStatus.ISSUED);
            execution.setInvoiceIssued(invoiceIssued);
            execution.setInvoiceReferenceCode(inv.getReferenceCode());
            execution.setInvoiceNumber(inv.getInvoiceNumber());

            boolean hasRefCode = inv.getReferenceCode() != null && !inv.getReferenceCode().isBlank();
            boolean hasInvNum = inv.getInvoiceNumber() != null && !inv.getInvoiceNumber().isBlank();

            if (!invoiceIssued) {
                failureReasons.append("Hóa đơn điện tử chưa ở trạng thái ISSUED (hiện tại: ").append(inv.getStatus()).append("). ");
            }
            if (!hasRefCode) {
                failureReasons.append("Hóa đơn thiếu mã reference_code. ");
            }
            if (!hasInvNum) {
                failureReasons.append("Hóa đơn chưa được cấp số invoice_number chính thức. ");
            }

            invoiceReconciled = (inv.getReconciliationStatus() == ReconciliationStatus.RECONCILED);
            execution.setInvoiceReconciled(invoiceReconciled);
            if (!invoiceReconciled) {
                failureReasons.append("Hóa đơn chưa được đối soát thành công (reconciliationStatus hiện tại: ").append(inv.getReconciliationStatus()).append("). ");
            }

            // PDF, XML, Email telemetry
            execution.setPdfStatus(inv.getPdfUrl() != null && !inv.getPdfUrl().isBlank() ? "AVAILABLE" : "NOT_AVAILABLE");
            execution.setXmlStatus(inv.getXmlUrl() != null && !inv.getXmlUrl().isBlank() ? "AVAILABLE" : "NOT_AVAILABLE");
            execution.setEmailStatus(inv.getBuyerEmail() != null && !inv.getBuyerEmail().isBlank() ? "CONFIGURED" : "NOT_CONFIGURED");
        }

        // Final Milestone Gate: All 7 minimum conditions must pass
        boolean allPassed = orderPaid
                && paymentSuccess
                && execution.getPaymentTransactionId() != null
                && enrollmentActive
                && invoiceIssued
                && execution.getInvoiceReferenceCode() != null
                && execution.getInvoiceNumber() != null
                && invoiceReconciled;

        if (allPassed) {
            execution.setStatus(PilotResultStatus.PASSED);
            execution.setFailureReason(null);
            log.info("[PILOT PASSED] Đơn hàng {} hoàn thành xuất sắc toàn bộ 7 tiêu chí kiểm chứng Pilot!", order.getOrderCode());
        } else {
            execution.setStatus(PilotResultStatus.FAILED);
            execution.setFailureReason(failureReasons.toString().trim());
            log.warn("[PILOT FAILED] Đơn hàng {} không đạt kiểm thử Pilot: {}", order.getOrderCode(), execution.getFailureReason());
        }

        PilotExecution savedExecution = pilotExecutionRepository.save(execution);

        // Update BillingSetting with latest pilot milestone
        BillingSetting setting = billingSettingRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        setting.setLastPilotExecutionId(savedExecution.getId());
        setting.setPilotStatus(savedExecution.getStatus());
        setting.setUpdatedAt(OffsetDateTime.now());
        billingSettingRepository.save(setting);

        return savedExecution;
    }

    public Optional<PilotExecution> getLatestPilotExecution() {
        return pilotExecutionRepository.findFirstByOrderByEvaluatedAtDesc();
    }

    /**
     * Strictly controls transitions between the 5 activation states.
     * Enforces that PRODUCTION_ACTIVE can only be activated after:
     * 1) A successful Pilot execution exists (PilotResult == PASSED)
     * 2) Readiness check has 0 failures
     * 3) Current state is PRODUCTION_PILOT
     * 4) Explicit admin confirmation is provided
     */
    @Transactional
    public BillingSetting transitionActivationState(
            ProductionActivationState targetState,
            boolean explicitAdminConfirmation,
            boolean hasReadinessFailures,
            String adminUser
    ) {
        BillingSetting setting = billingSettingRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(BillingSetting::new);
        ProductionActivationState current = setting.getActivationState() != null
                ? setting.getActivationState()
                : ProductionActivationState.SANDBOX;

        if (targetState == ProductionActivationState.PRODUCTION_PILOT) {
            if (hasReadinessFailures) {
                throw new BusinessRuleException("Không thể chuyển sang PRODUCTION_PILOT vì còn tiêu chí FAIL trong báo cáo kiểm tra Go-Live!");
            }
        } else if (targetState == ProductionActivationState.PRODUCTION_ACTIVE) {
            // Rule 1: Must be coming from PRODUCTION_PILOT
            if (current != ProductionActivationState.PRODUCTION_PILOT) {
                throw new BusinessRuleException("Chỉ có thể kích hoạt PRODUCTION_ACTIVE sau khi đã trải qua giai đoạn PRODUCTION_PILOT!");
            }

            // Rule 2: Explicit Admin Confirmation is mandatory
            if (!explicitAdminConfirmation) {
                throw new BusinessRuleException("Kích hoạt PRODUCTION_ACTIVE bắt buộc phải có xác nhận tường minh của Quản trị viên (explicitAdminConfirmation = true)!");
            }

            // Rule 3: Pilot Execution must exist and be PASSED
            Optional<PilotExecution> latestPilotOpt = getLatestPilotExecution();
            if (latestPilotOpt.isEmpty() || latestPilotOpt.get().getStatus() == PilotResultStatus.NOT_STARTED) {
                throw new BusinessRuleException("Không thể kích hoạt PRODUCTION_ACTIVE khi chưa thực hiện và vượt qua đợt thử nghiệm Pilot nào (PilotResult = NOT_STARTED)!");
            }

            PilotExecution latestPilot = latestPilotOpt.get();
            if (latestPilot.getStatus() == PilotResultStatus.IN_PROGRESS) {
                throw new BusinessRuleException("Không thể kích hoạt PRODUCTION_ACTIVE khi đợt thử nghiệm Pilot vẫn đang xử lý (PilotResult = IN_PROGRESS)!");
            }
            if (latestPilot.getStatus() == PilotResultStatus.FAILED) {
                throw new BusinessRuleException("Không thể kích hoạt PRODUCTION_ACTIVE vì đợt thử nghiệm Pilot gần nhất bị thất bại: " + latestPilot.getFailureReason());
            }
            if (latestPilot.getStatus() == PilotResultStatus.REQUIRES_REVIEW) {
                throw new BusinessRuleException("Không thể kích hoạt PRODUCTION_ACTIVE vì đợt thử nghiệm Pilot gần nhất đang yêu cầu kế toán đối soát lại (REQUIRES_REVIEW)!");
            }
            if (latestPilot.getStatus() != PilotResultStatus.PASSED) {
                throw new BusinessRuleException("Không thể kích hoạt PRODUCTION_ACTIVE vì đợt thử nghiệm Pilot chưa đạt chuẩn PASSED!");
            }

            // Rule 4: Zero Readiness Failures
            if (hasReadinessFailures) {
                throw new BusinessRuleException("Không thể kích hoạt PRODUCTION_ACTIVE vì vẫn còn tiêu chí FAIL trong báo cáo kiểm tra Go-Live!");
            }
        }

        setting.setActivationState(targetState);
        setting.setIsSandbox(targetState == ProductionActivationState.SANDBOX);
        setting.setUpdatedAt(OffsetDateTime.now());

        BillingSetting saved = billingSettingRepository.save(setting);
        log.info("[ACTIVATION STATE] {} -> {} (explicitAdminConfirmation={}, adminUser={})",
                current, targetState, explicitAdminConfirmation, adminUser);
        return saved;
    }
}
