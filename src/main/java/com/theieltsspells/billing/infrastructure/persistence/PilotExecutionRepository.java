package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.PilotExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PilotExecutionRepository extends JpaRepository<PilotExecution, UUID> {
    Optional<PilotExecution> findFirstByOrderByEvaluatedAtDesc();
    Optional<PilotExecution> findFirstByOrderIdOrderByEvaluatedAtDesc(UUID orderId);
}
