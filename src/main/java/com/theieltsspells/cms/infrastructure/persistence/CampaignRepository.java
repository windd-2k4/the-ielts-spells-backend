package com.theieltsspells.cms.infrastructure.persistence;

import com.theieltsspells.cms.domain.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    Page<Campaign> findByIsActive(Boolean isActive, Pageable pageable);
    Optional<Campaign> findByCampaignCode(String campaignCode);
}
