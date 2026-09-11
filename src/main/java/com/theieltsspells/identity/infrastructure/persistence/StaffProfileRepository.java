package com.theieltsspells.identity.infrastructure.persistence;

import com.theieltsspells.identity.domain.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<StaffProfile> findByEmailIgnoreCase(String email);
    Optional<StaffProfile> findByAuthUserId(UUID authUserId);
}
