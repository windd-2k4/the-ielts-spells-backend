package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID>, JpaSpecificationExecutor<PaymentTransaction> {

    boolean existsBySepayTransactionId(String sepayTransactionId);

    Optional<PaymentTransaction> findBySepayTransactionId(String sepayTransactionId);

    Optional<PaymentTransaction> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<PaymentTransaction> findFirstByOrderCodeOrderByCreatedAtDesc(String orderCode);

    @Query("""
            select coalesce(sum(t.amountIn), 0)
            from PaymentTransaction t
            where t.orderId = :orderId
              and t.id <> :excludedTransactionId
              and t.status in (
                com.theieltsspells.billing.domain.PaymentTransactionStatus.SUCCESS,
                com.theieltsspells.billing.domain.PaymentTransactionStatus.PARTIAL_PAYMENT,
                com.theieltsspells.billing.domain.PaymentTransactionStatus.OVERPAID
              )
            """)
    BigDecimal sumCapturedAmountByOrderIdExcluding(
            @Param("orderId") UUID orderId,
            @Param("excludedTransactionId") UUID excludedTransactionId
    );
}
