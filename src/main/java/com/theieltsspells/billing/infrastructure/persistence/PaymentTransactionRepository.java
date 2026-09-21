package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID>, JpaSpecificationExecutor<PaymentTransaction> {

    boolean existsBySepayTransactionId(String sepayTransactionId);

    Optional<PaymentTransaction> findBySepayTransactionId(String sepayTransactionId);
}
