package com.theieltsspells.identity.infrastructure.persistence;

import com.theieltsspells.identity.domain.TeacherProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, UUID> {

    @Modifying
    @Query(value = """
            insert into public.teacher_profiles (user_id, teacher_code)
            values (:userId, :teacherCode)
            on conflict (user_id) do nothing
            """, nativeQuery = true)
    void ensureProfile(@Param("userId") UUID userId, @Param("teacherCode") String teacherCode);
}

