package com.theieltsspells.learninglibrary.infrastructure.persistence;

import com.theieltsspells.learninglibrary.domain.LearningResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface LearningResourceRepository extends JpaRepository<LearningResource, UUID>, JpaSpecificationExecutor<LearningResource> {
}
