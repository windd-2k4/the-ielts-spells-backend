package com.theieltsspells.identity.infrastructure.persistence;
import com.theieltsspells.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into public.user_roles (user_id, role, assigned_by, assigned_at)
            values (:userId, cast(:role as app_role), :assignedBy, :assignedAt)
            on conflict (user_id, role) do update
            set assigned_by = excluded.assigned_by,
                assigned_at = excluded.assigned_at
            """, nativeQuery = true)
    void upsertRole(@Param("userId") UUID userId,
                    @Param("role") String role,
                    @Param("assignedBy") UUID assignedBy,
                    @Param("assignedAt") OffsetDateTime assignedAt);

    @Modifying(flushAutomatically = true)
    @Query(value = "delete from public.user_roles where user_id = :userId and role = cast(:role as app_role)",
            nativeQuery = true)
    void deleteRole(@Param("userId") UUID userId, @Param("role") String role);
}
