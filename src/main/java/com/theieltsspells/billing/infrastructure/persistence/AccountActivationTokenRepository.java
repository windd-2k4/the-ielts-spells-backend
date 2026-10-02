package com.theieltsspells.billing.infrastructure.persistence;

import com.theieltsspells.billing.domain.AccountActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountActivationTokenRepository extends JpaRepository<AccountActivationToken, UUID> {

    Optional<AccountActivationToken> findByTokenHash(String tokenHash);

    Optional<AccountActivationToken> findByOrderId(UUID orderId);
}
