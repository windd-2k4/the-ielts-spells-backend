package com.theieltsspells.cms.infrastructure.persistence;

import com.theieltsspells.cms.domain.CmsPost;
import com.theieltsspells.shared.persistence.enums.PublishStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CmsPostRepository extends JpaRepository<CmsPost, UUID> {
    Page<CmsPost> findByStatus(PublishStatus status, Pageable pageable);
    Optional<CmsPost> findBySlug(String slug);
}
