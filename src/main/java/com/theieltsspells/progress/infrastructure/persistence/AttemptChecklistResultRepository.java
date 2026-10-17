package com.theieltsspells.progress.infrastructure.persistence;

import com.theieltsspells.progress.domain.AttemptChecklistResult;
import com.theieltsspells.progress.domain.AttemptChecklistResultId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AttemptChecklistResultRepository extends JpaRepository<AttemptChecklistResult, AttemptChecklistResultId> {
    List<AttemptChecklistResult> findByAttemptIdIn(Collection<UUID> attemptIds);
}
