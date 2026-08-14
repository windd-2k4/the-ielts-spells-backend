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
            where (:active is null or profile.isActive = :active) and (:query = ''
              or lower(profile.fullName) like lower(concat('%', :query, '%'))
              or lower(coalesce(profile.email, '')) like lower(concat('%', :query, '%'))
              or (:phoneQuery <> '' and replace(replace(replace(coalesce(profile.phone, ''), ' ', ''), '.', ''), '-', '') like concat('%', :phoneQuery, '%')))
            """, countQuery = """
            select count(student) from StudentProfile student join student.userRef profile
            where (:active is null or profile.isActive = :active) and (:query = ''
              or lower(profile.fullName) like lower(concat('%', :query, '%'))
              or lower(coalesce(profile.email, '')) like lower(concat('%', :query, '%'))
              or (:phoneQuery <> '' and replace(replace(replace(coalesce(profile.phone, ''), ' ', ''), '.', ''), '-', '') like concat('%', :phoneQuery, '%')))
            """)
    Page<StudentProfile> searchStudents(@Param("query") String query,
                                        @Param("phoneQuery") String phoneQuery,
                                        @Param("active") Boolean active,
                                        Pageable pageable);

    @Query(value = """
            with student_directory as (
              select sp.user_id,
                     sp.student_code,
                     p.full_name,
                     p.email,
                     p.phone,
                     p.avatar_path,
                     sp.current_band,
                     sp.target_band,
                     p.is_active as profile_active,
                     sp.joined_at,
                     case
                       when exists (select 1 from enrollments e where e.student_id = sp.user_id and e.status::text = 'ACTIVE') then 'ACTIVE'
                       when exists (select 1 from enrollments e where e.student_id = sp.user_id and e.status::text = 'PENDING') then 'PENDING'
                       when exists (select 1 from enrollments e where e.student_id = sp.user_id and e.status::text = 'PAUSED') then 'PAUSED'
                       when exists (select 1 from enrollments e where e.student_id = sp.user_id and e.status::text = 'COMPLETED') then 'COMPLETED'
                       when exists (select 1 from enrollments e where e.student_id = sp.user_id and e.status::text = 'WITHDRAWN') then 'WITHDRAWN'
                       else 'NONE'
                     end as lifecycle_status,
                     coalesce((select count(*) from enrollments e where e.student_id = sp.user_id), 0) as enrollment_count,
                     latest.course_id as current_course_id,
                     latest.course_code as current_course_code,
                     latest.course_name as current_course_name
              from student_profiles sp
              join profiles p on p.id = sp.user_id
              left join lateral (
                select e.course_id, c.code as course_code, c.name as course_name
                from enrollments e
                join courses c on c.id = e.course_id
                where e.student_id = sp.user_id
                order by case e.status::text
                           when 'ACTIVE' then 1
                           when 'PENDING' then 2
                           when 'PAUSED' then 3
                           when 'COMPLETED' then 4
                           when 'WITHDRAWN' then 5
                           else 6
                         end,
                         e.enrolled_at desc
                limit 1
              ) latest on true
            )
            select d.user_id as "id", d.student_code as "studentCode", d.full_name as "fullName",
                   d.email as "email", d.phone as "phone", d.avatar_path as "avatarPath",
                   d.current_band as "currentBand", d.target_band as "targetBand", d.profile_active as "active",
                   d.lifecycle_status as "lifecycleStatus", d.current_course_id as "currentCourseId",
                   d.current_course_code as "currentCourseCode", d.current_course_name as "currentCourseName",
                   d.enrollment_count as "enrollmentCount", d.joined_at as "joinedAt"
            from student_directory d
            where (cast(:active as boolean) is null or d.profile_active = :active)
              and (cast(:lifecycle as text) is null or d.lifecycle_status = :lifecycle)
              and (:query = ''
                or lower(d.full_name) like lower(concat('%', :query, '%'))
                or lower(coalesce(d.email, '')) like lower(concat('%', :query, '%'))
                or lower(d.student_code) like lower(concat('%', :query, '%'))
                or (:phoneQuery <> '' and regexp_replace(coalesce(d.phone, ''), '[^0-9+]', '', 'g') like concat('%', :phoneQuery, '%')))
            order by lower(d.full_name), d.student_code
            """, countQuery = """
            with student_directory as (
              select sp.user_id, sp.student_code, p.full_name, p.email, p.phone, p.is_active as profile_active,
                     case
                       when exists (select 1 from enrollments e where e.student_id = sp.user_id and e.status::text = 'ACTIVE') then 'ACTIVE'
                       when exists (select 1 from enrollments e where e.student_id = sp.user_id and e.status::text = 'PENDING') then 'PENDING'
                       when exists (select 1 from enrollments e where e.student_id = sp.user_id and e.status::text = 'PAUSED') then 'PAUSED'
                       when exists (select 1 from enrollments e where e.student_id = sp.user_id and e.status::text = 'COMPLETED') then 'COMPLETED'
                       when exists (select 1 from enrollments e where e.student_id = sp.user_id and e.status::text = 'WITHDRAWN') then 'WITHDRAWN'
                       else 'NONE'
                     end as lifecycle_status
              from student_profiles sp
              join profiles p on p.id = sp.user_id
            )
            select count(*) from student_directory d
            where (cast(:active as boolean) is null or d.profile_active = :active)
              and (cast(:lifecycle as text) is null or d.lifecycle_status = :lifecycle)
              and (:query = ''
                or lower(d.full_name) like lower(concat('%', :query, '%'))
                or lower(coalesce(d.email, '')) like lower(concat('%', :query, '%'))
                or lower(d.student_code) like lower(concat('%', :query, '%'))
                or (:phoneQuery <> '' and regexp_replace(coalesce(d.phone, ''), '[^0-9+]', '', 'g') like concat('%', :phoneQuery, '%')))
            """, nativeQuery = true)
    Page<StudentDirectoryProjection> searchDirectory(@Param("query") String query,
                                                      @Param("phoneQuery") String phoneQuery,
                                                      @Param("active") Boolean active,
                                                      @Param("lifecycle") String lifecycle,
                                                      Pageable pageable);
}
