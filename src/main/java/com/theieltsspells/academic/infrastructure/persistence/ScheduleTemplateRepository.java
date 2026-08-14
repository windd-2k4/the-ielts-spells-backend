package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.ScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, UUID> {
    List<ScheduleTemplate> findBySkillPairAndActiveTrueOrderByCreatedAtAsc(String skillPair);
}
