package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID>, JpaSpecificationExecutor<PaymentTransaction> {

    boolean existsBySepayTransactionId(String sepayTransactionId);

    Optional<PaymentTransaction> findBySepayTransactionId(String sepayTransactionId);

    List<PaymentTransaction> findBySepayTransactionIdIn(Collection<String> sepayTransactionIds);

    Optional<PaymentTransaction> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<PaymentTransaction> findFirstByOrderCodeOrderByCreatedAtDesc(String orderCode);

    @Query(value = """
            select coalesce(sum(t.amount_in), 0)
            from payment_transactions t
            where t.order_id = :orderId
              and t.id <> :excludedTransactionId
              -- Compare the enum value as text so this query is portable across
              -- environments where the PostgreSQL enum type has a different
              -- qualified name (or was created by Supabase with a legacy name).
              and t.status::text in ('SUCCESS', 'PARTIAL_PAYMENT', 'OVERPAID')
            """, nativeQuery = true)
    BigDecimal sumCapturedAmountByOrderIdExcluding(
            @Param("orderId") UUID orderId,
            @Param("excludedTransactionId") UUID excludedTransactionId
    );
}
