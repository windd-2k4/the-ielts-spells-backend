package com.theieltsspells.identity.domain;

import com.theieltsspells.shared.persistence.enums.*;
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
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class StudentProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "student_code", nullable = false)
    private String studentCode;

    @Column(name = "current_band")
    private BigDecimal currentBand;

    @Column(name = "target_band")
    private BigDecimal targetBand;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "address")
    private String address;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "emergency_contact", nullable = false)
    private Map<String, Object> emergencyContact;

    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    @Column(name = "notes")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private Profile userRef;
}
