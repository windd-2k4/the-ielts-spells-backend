package com.theieltsspells.admissions.domain;

import com.theieltsspells.shared.persistence.enums.*;
import com.theieltsspells.cms.domain.Campaign;
import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.identity.domain.StudentProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class Lead {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "current_band")
    private BigDecimal currentBand;

    @Column(name = "target_band")
    private BigDecimal targetBand;

    @Column(name = "interested_course_id")
    private UUID interestedCourseId;

    @Column(name = "preferred_contact_at")
    private OffsetDateTime preferredContactAt;

    @Column(name = "source")
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "utm_data", nullable = false)
    private Map<String, Object> utmData;

    @Column(name = "consent_at")
    private OffsetDateTime consentAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private LeadStatus status;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "converted_student_id")
    private UUID convertedStudentId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", insertable = false, updatable = false)
    private Campaign campaignRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to", insertable = false, updatable = false)
    private Profile assignedToRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_student_id", insertable = false, updatable = false)
    private StudentProfile convertedStudentRef;
}
