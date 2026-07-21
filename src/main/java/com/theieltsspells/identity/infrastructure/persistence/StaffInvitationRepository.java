package com.theieltsspells.identity.infrastructure.persistence;

import com.theieltsspells.identity.domain.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, UUID> {
    boolean existsByEmailIgnoreCaseAndStatus(String email, InvitationStatus status);
    Optional<StaffInvitation> findFirstByEmailIgnoreCaseAndStatusOrderByInvitedAtDesc(String email, InvitationStatus status);
    Optional<StaffInvitation> findFirstByStaffProfileIdAndStatusOrderByInvitedAtDesc(UUID staffProfileId,
                                                                                     InvitationStatus status);
    Page<StaffInvitation> findAllByStatus(InvitationStatus status, Pageable pageable);
}
