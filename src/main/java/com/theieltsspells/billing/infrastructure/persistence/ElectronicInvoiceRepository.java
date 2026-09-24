package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ElectronicInvoiceRepository extends JpaRepository<ElectronicInvoice, UUID>, JpaSpecificationExecutor<ElectronicInvoice> {

    Optional<ElectronicInvoice> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    @Query(value = "SELECT * FROM electronic_invoices WHERE order_id = :orderId ORDER BY created_at DESC LIMIT 1 FOR UPDATE", nativeQuery = true)
    Optional<ElectronicInvoice> findLatestByOrderIdForUpdate(@Param("orderId") UUID orderId);

    Optional<ElectronicInvoice> findByPaymentTransactionId(UUID paymentTransactionId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ElectronicInvoice e WHERE e.paymentTransactionId = :paymentTransactionId")
    Optional<ElectronicInvoice> findByPaymentTransactionIdForUpdate(@Param("paymentTransactionId") UUID paymentTransactionId);

    Optional<ElectronicInvoice> findByReferenceCode(String referenceCode);

    List<ElectronicInvoice> findByReconciliationStatus(com.theieltsspells.billing.domain.ReconciliationStatus status);

    Optional<ElectronicInvoice> findByLookupCode(String lookupCode);

    Optional<ElectronicInvoice> findByCreateTrackingCode(String createTrackingCode);

    Optional<ElectronicInvoice> findByIssueTrackingCode(String issueTrackingCode);

    List<ElectronicInvoice> findByStatusInAndRetryCountLessThan(List<InvoiceStatus> statuses, int maxRetries);

    @Query("""
            SELECT e FROM ElectronicInvoice e
            WHERE (e.status IN :statuses OR e.reconciliationStatus = com.theieltsspells.billing.domain.ReconciliationStatus.PENDING)
              AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
              AND e.retryCount < :maxRetries
            ORDER BY e.createdAt ASC
            """)
    List<ElectronicInvoice> findActionableInvoices(
            @Param("statuses") List<InvoiceStatus> statuses,
            @Param("now") OffsetDateTime now,
            @Param("maxRetries") int maxRetries
    );

    @Query("SELECT e FROM ElectronicInvoice e WHERE e.status IN :statuses")
    List<ElectronicInvoice> findByStatusIn(@Param("statuses") List<InvoiceStatus> statuses);
}
