package com.theieltsspells.cms.infrastructure.persistence;

import com.theieltsspells.cms.domain.CmsPage;
import com.theieltsspells.shared.persistence.enums.PublishStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface CmsPageRepository extends JpaRepository<CmsPage, UUID> {
    Page<CmsPage> findByStatus(PublishStatus status, Pageable pageable);
    Optional<CmsPage> findBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select page from CmsPage page where page.id = :id")
    Optional<CmsPage> findByIdForUpdate(@Param("id") UUID id);
}
