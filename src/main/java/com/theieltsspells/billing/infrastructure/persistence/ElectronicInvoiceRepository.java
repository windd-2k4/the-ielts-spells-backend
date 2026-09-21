package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ElectronicInvoiceRepository extends JpaRepository<ElectronicInvoice, UUID>, JpaSpecificationExecutor<ElectronicInvoice> {

    Optional<ElectronicInvoice> findByOrderId(UUID orderId);

    Optional<ElectronicInvoice> findByLookupCode(String lookupCode);

    List<ElectronicInvoice> findByStatusInAndRetryCountLessThan(List<InvoiceStatus> statuses, int maxRetries);
}
