package com.theieltsspells.learninglibrary.infrastructure.persistence;

import com.theieltsspells.learninglibrary.domain.LearningResourceFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface LearningResourceFileRepository extends JpaRepository<LearningResourceFile, UUID>, JpaSpecificationExecutor<LearningResourceFile> {
    List<LearningResourceFile> findByResourceIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID resourceId);
}
