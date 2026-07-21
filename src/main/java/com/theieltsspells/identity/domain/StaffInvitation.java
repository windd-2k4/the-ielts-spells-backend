package com.theieltsspells.identity.domain;

import com.theieltsspells.identity.infrastructure.persistence.AppRoleConverter;
import com.theieltsspells.shared.persistence.enums.AppRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "staff_invitations")
@Getter @Setter @NoArgsConstructor
public class StaffInvitation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "staff_profile_id", nullable = false) private UUID staffProfileId;
    @Column(nullable = false) private String email;
    @Convert(converter = AppRoleConverter.class)
    @ColumnTransformer(write = "?::app_role")
    @Column(name = "intended_role", nullable = false) private AppRole intendedRole;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private InvitationStatus status;
    @Column(name = "invited_by") private UUID invitedBy;
    @Column(name = "invited_at", nullable = false) private OffsetDateTime invitedAt;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "accepted_at") private OffsetDateTime acceptedAt;
    @Column(name = "revoked_at") private OffsetDateTime revokedAt;
    @Column(name = "supabase_user_id") private UUID supabaseUserId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
}
