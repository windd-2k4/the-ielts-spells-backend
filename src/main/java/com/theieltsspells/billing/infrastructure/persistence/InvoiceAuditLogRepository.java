package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.InvoiceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceAuditLogRepository extends JpaRepository<InvoiceAuditLog, UUID> {
    List<InvoiceAuditLog> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
}
