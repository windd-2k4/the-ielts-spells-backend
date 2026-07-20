package com.theieltsspells.identity.infrastructure.persistence;

import com.theieltsspells.identity.domain.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByEmailIgnoreCase(String email);

    Optional<Profile> findByPhone(String phone);

    Page<Profile> findByIsActive(Boolean isActive, Pageable pageable);

    boolean existsByEmailIgnoreCase(String email);
}
