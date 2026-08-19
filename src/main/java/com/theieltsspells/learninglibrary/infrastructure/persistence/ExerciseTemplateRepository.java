package com.theieltsspells.learninglibrary.infrastructure.persistence;

import com.theieltsspells.learninglibrary.domain.ExerciseTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ExerciseTemplateRepository extends JpaRepository<ExerciseTemplate, UUID>, JpaSpecificationExecutor<ExerciseTemplate> {
}
