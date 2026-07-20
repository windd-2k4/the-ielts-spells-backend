package com.theieltsspells.testing.domain;

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
@Table(name = "test_sections")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class TestSection {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "skill", nullable = false)
    private SkillType skill;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "instructions")
    private String instructions;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    @Column(name = "duration_minutes")
    private Short durationMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", insertable = false, updatable = false)
    private Test testRef;
}
