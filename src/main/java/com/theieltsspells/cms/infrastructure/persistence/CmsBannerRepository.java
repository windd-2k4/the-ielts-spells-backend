package com.theieltsspells.cms.infrastructure.persistence;

import com.theieltsspells.cms.domain.CmsBanner;
import com.theieltsspells.shared.persistence.enums.PublishStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CmsBannerRepository extends JpaRepository<CmsBanner, UUID> {
    Page<CmsBanner> findByStatus(PublishStatus status, Pageable pageable);
}
