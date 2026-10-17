package com.theieltsspells.progress.infrastructure.persistence;

import com.theieltsspells.progress.domain.ActivityEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ActivityEvidenceRepository extends JpaRepository<ActivityEvidence, UUID> {
    List<ActivityEvidence> findByAttemptIdIn(Collection<UUID> attemptIds);
}
