package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.BillingSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingSettingRepository extends JpaRepository<BillingSetting, UUID> {

    Optional<BillingSetting> findFirstByOrderByUpdatedAtDesc();

    default Optional<BillingSetting> findLatest() {
        return findFirstByOrderByUpdatedAtDesc();
    }
}
