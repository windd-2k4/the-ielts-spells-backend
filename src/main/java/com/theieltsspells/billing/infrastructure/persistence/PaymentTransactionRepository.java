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

    @Query(value = """
            select coalesce(sum(t.amount_in), 0)
            from payment_transactions t
            where t.order_id = :orderId
              and t.id <> :excludedTransactionId
              and t.status in (
                cast('SUCCESS' as public.payment_transaction_status),
                cast('PARTIAL_PAYMENT' as public.payment_transaction_status),
                cast('OVERPAID' as public.payment_transaction_status)
              )
            """, nativeQuery = true)
    BigDecimal sumCapturedAmountByOrderIdExcluding(
            @Param("orderId") UUID orderId,
            @Param("excludedTransactionId") UUID excludedTransactionId
    );
}
