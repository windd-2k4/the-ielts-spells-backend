package com.theieltsspells.identity.domain;

import com.theieltsspells.identity.infrastructure.persistence.AppRoleConverter;
import com.theieltsspells.shared.persistence.enums.AppRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "staff_profiles")
@Getter @Setter @NoArgsConstructor
public class StaffProfile {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "auth_user_id") private UUID authUserId;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(nullable = false) private String email;
    private String phone;
    @Column(name = "avatar_path") private String avatarPath;
    @Column(name = "job_title") private String jobTitle;
    private String department;
    @Column(name = "employment_type") private String employmentType;
    @Column(name = "start_date") private LocalDate startDate;
    @Convert(converter = AppRoleConverter.class)
    @ColumnTransformer(write = "?::app_role")
    @Column(name = "primary_role", nullable = false) private AppRole primaryRole;
    @Column(name = "cv_path") private String cvPath;
    @Column(name = "portfolio_url") private String portfolioUrl;
    @Column(name = "professional_summary") private String professionalSummary;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String certificates = "[]";
    @Column(name = "internal_notes") private String internalNotes;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StaffStatus status;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "activated_at") private OffsetDateTime activatedAt;
}
