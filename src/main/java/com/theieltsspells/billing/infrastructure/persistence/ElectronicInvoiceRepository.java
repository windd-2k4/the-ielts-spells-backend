package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.InvoiceStatus;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT e FROM ElectronicInvoice e LEFT JOIN FETCH e.paymentTransaction WHERE e.id IN :ids")
    List<ElectronicInvoice> findAllWithPaymentTransactionByIdIn(@Param("ids") List<UUID> ids);

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
            @Param("maxRetries") int maxRetries,
            Pageable pageable
    );

    @Query("""
            SELECT e FROM ElectronicInvoice e
            WHERE e.status IN :statuses
              AND e.referenceCode IS NOT NULL
              AND e.createdAt >= :createdAfter
              AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
            ORDER BY COALESCE(e.lastStatusCheckedAt, e.createdAt) ASC
            """)
    List<ElectronicInvoice> findStatusReconciliationCandidates(
            @Param("statuses") List<InvoiceStatus> statuses,
            @Param("createdAfter") OffsetDateTime createdAfter,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    @Query(value = """
            SELECT
                COUNT(*) AS "totalCount",
                COUNT(*) FILTER (WHERE status::text = 'ISSUED') AS "issuedCount",
                COUNT(*) FILTER (WHERE status::text IN ('PENDING_ISSUE', 'PILOT_PENDING_APPROVAL', 'CREATING', 'PROCESSING', 'ISSUING', 'DRAFT')) AS "pendingCount",
                COUNT(*) FILTER (WHERE status::text IN ('FAILED', 'UNKNOWN')) AS "failedCount",
                COUNT(*) FILTER (WHERE status::text = 'CANCELLED') AS "cancelledCount",
                COALESCE(SUM(total_amount) FILTER (WHERE status::text = 'ISSUED'), 0) AS "totalInvoicedAmount"
            FROM electronic_invoices
            WHERE (CAST(:fromTime AS timestamptz) IS NULL OR created_at >= CAST(:fromTime AS timestamptz))
              AND (CAST(:toTime AS timestamptz) IS NULL OR created_at < CAST(:toTime AS timestamptz))
            """, nativeQuery = true)
    InvoiceStatsProjection aggregateStats(
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime
    );

    interface InvoiceStatsProjection {
        long getTotalCount();
        long getIssuedCount();
        long getPendingCount();
        long getFailedCount();
        long getCancelledCount();
        java.math.BigDecimal getTotalInvoicedAmount();
    }

    @Query(value = """
            SELECT * FROM electronic_invoices
            WHERE status = 'ISSUED'
              AND (cqt_code IS NULL OR BTRIM(cqt_code) = '')
              AND xml_url IS NOT NULL
              AND issued_at >= :issuedAfter
              AND (next_retry_at IS NULL OR next_retry_at <= :now)
            ORDER BY issued_at ASC
            LIMIT 15
            """, nativeQuery = true)
    List<ElectronicInvoice> findIssuedInvoicesAwaitingCqt(
            @Param("issuedAfter") OffsetDateTime issuedAfter,
            @Param("now") OffsetDateTime now
    );

    @Query("SELECT e FROM ElectronicInvoice e WHERE e.status IN :statuses")
    List<ElectronicInvoice> findByStatusIn(@Param("statuses") List<InvoiceStatus> statuses);
}
