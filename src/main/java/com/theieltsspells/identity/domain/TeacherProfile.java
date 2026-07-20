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
@Table(name = "teacher_profiles")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class TeacherProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "teacher_code", nullable = false)
    private String teacherCode;

    @Column(name = "bio")
    private String bio;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "specialties", nullable = false)
    private List<String> specialties;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "certificates", nullable = false)
    private Map<String, Object> certificates;

    @Column(name = "years_experience")
    private Short yearsExperience;

    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    @Column(name = "notes")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private Profile userRef;
}
