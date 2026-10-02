package com.theieltsspells.billing.application;

import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.InvoiceStatus;
import com.theieltsspells.billing.domain.Order;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElectronicInvoiceWorker {

    private final ElectronicInvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final BillingSettingRepository billingSettingRepository;
    private final ElectronicInvoiceService invoiceService;

    private static final List<InvoiceStatus> ACTIONABLE_STATUSES = List.of(
            InvoiceStatus.PENDING_ISSUE,
            InvoiceStatus.CREATING,
            InvoiceStatus.PROCESSING,
            InvoiceStatus.ISSUING
    );

    /**
     * Polling định kỳ mỗi 5 giây kiểm tra các hóa đơn đang chờ tạo hoặc đang chờ SePay cấp mã
     */
    @Scheduled(fixedDelay = 5000)
    public void processPendingInvoices() {
        boolean autoInvoiceEnabled = billingSettingRepository.findLatest()
                .map(setting -> Boolean.TRUE.equals(setting.getAutoInvoiceEnabled()))
                .orElse(false);
        if (!autoInvoiceEnabled) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<ElectronicInvoice> candidates = invoiceRepository.findActionableInvoices(ACTIONABLE_STATUSES, now, 15);

        if (candidates.isEmpty()) {
            return;
        }

        log.debug("ElectronicInvoiceWorker: Tìm thấy {} hóa đơn cần xử lý hoặc kiểm tra trạng thái", candidates.size());

        for (ElectronicInvoice invoice : candidates) {
            try {
                if (invoice.getReconciliationStatus() == com.theieltsspells.billing.domain.ReconciliationStatus.PENDING) {
                    invoiceService.checkAndAdvanceInvoice(invoice.getId());
                } else if (invoice.getStatus() == InvoiceStatus.PENDING_ISSUE) {
                    Order order = invoice.getOrderId() != null
                            ? orderRepository.findById(invoice.getOrderId()).orElse(null)
                            : null;
                    invoiceService.executeCreateInvoice(invoice, order);
                } else if (invoice.getStatus() == InvoiceStatus.CREATING
                        || invoice.getStatus() == InvoiceStatus.PROCESSING
                        || invoice.getStatus() == InvoiceStatus.ISSUING) {
                    invoiceService.checkAndAdvanceInvoice(invoice.getId());
                }
            } catch (Exception ex) {
                log.error("Lỗi xử lý hóa đơn {} trong worker: {}", invoice.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Phục hồi các tác vụ hóa đơn dở dang khi server restart
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedJobsOnStartup() {
        List<ElectronicInvoice> inFlight = invoiceRepository.findByStatusIn(List.of(
                InvoiceStatus.CREATING,
                InvoiceStatus.PROCESSING,
                InvoiceStatus.ISSUING
        ));

        if (inFlight.isEmpty()) {
            return;
        }

        log.info("Khởi động hệ thống: Phát hiện {} hóa đơn đang dở dang cần phục hồi tiến trình", inFlight.size());

        OffsetDateTime now = OffsetDateTime.now();
        for (ElectronicInvoice inv : inFlight) {
            if (inv.getStatus() == InvoiceStatus.CREATING && inv.getCreateTrackingCode() == null) {
                // Đang gửi request thì server tắt -> chuyển về PENDING_ISSUE để gửi lại an toàn với cùng reference_code
                inv.setStatus(InvoiceStatus.PENDING_ISSUE);
                inv.setNextRetryAt(now);
            } else {
                // Đã có tracking_code -> Đặt lịch kiểm tra kết quả ngay
                inv.setNextRetryAt(now);
            }
            invoiceRepository.save(inv);
        }

        log.info("Đã phục hồi lịch kiểm tra cho {} hóa đơn dở dang.", inFlight.size());
    }
}
