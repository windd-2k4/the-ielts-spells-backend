package com.theieltsspells.identity.infrastructure.persistence;

import com.theieltsspells.identity.domain.StudentProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    @Query("select student from StudentProfile student join fetch student.userRef where student.userId = :id")
    Optional<StudentProfile> findByIdWithProfile(@Param("id") UUID id);

    @Query(value = """
            select student from StudentProfile student join fetch student.userRef profile
            where profile.isActive = true and (:query = ''
              or lower(profile.fullName) like lower(concat('%', :query, '%'))
              or lower(coalesce(profile.email, '')) like lower(concat('%', :query, '%'))
              or (:phoneQuery <> '' and replace(replace(replace(coalesce(profile.phone, ''), ' ', ''), '.', ''), '-', '') like concat('%', :phoneQuery, '%')))
            """, countQuery = """
            select count(student) from StudentProfile student join student.userRef profile
            where profile.isActive = true and (:query = ''
              or lower(profile.fullName) like lower(concat('%', :query, '%'))
              or lower(coalesce(profile.email, '')) like lower(concat('%', :query, '%'))
              or (:phoneQuery <> '' and replace(replace(replace(coalesce(profile.phone, ''), ' ', ''), '.', ''), '-', '') like concat('%', :phoneQuery, '%')))
            """)
    Page<StudentProfile> searchActiveStudents(@Param("query") String query, @Param("phoneQuery") String phoneQuery, Pageable pageable);
}
