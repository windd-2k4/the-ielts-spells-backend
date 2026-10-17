package com.theieltsspells.curriculum.infrastructure.persistence;

import com.theieltsspells.curriculum.domain.ActivityChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ActivityChecklistItemRepository extends JpaRepository<ActivityChecklistItem, UUID> {
    List<ActivityChecklistItem> findByActivityIdInOrderByDisplayOrder(Collection<UUID> activityIds);
}
