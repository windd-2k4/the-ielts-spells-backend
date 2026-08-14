package com.theieltsspells.curriculum.infrastructure.persistence;

import com.theieltsspells.curriculum.domain.ClassActivity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ClassActivityRepository extends JpaRepository<ClassActivity, UUID> {
    @Query("select item from ClassActivity item join fetch item.activityRef activity where item.courseId = :courseId and item.isPublished = true order by activity.displayOrder")
    List<ClassActivity> findPublishedByCourseId(@Param("courseId") UUID courseId);
}
