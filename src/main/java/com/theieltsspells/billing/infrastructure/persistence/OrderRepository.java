package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeForUpdate(@Param("orderCode") String orderCode);

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("select o from Order o where o.status = 'PENDING_PAYMENT' and o.expiresAt < :now")
    List<Order> findExpiredPendingOrders(@Param("now") OffsetDateTime now);
}
