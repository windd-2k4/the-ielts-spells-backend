package com.theieltsspells.academic.domain;

import com.theieltsspells.shared.persistence.enums.*;
import com.theieltsspells.identity.domain.TeacherProfile;
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
@Table(name = "course_teachers")
@IdClass(ClassTeacherId.class)
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class ClassTeacher {

    @Id
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Id
    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "teaching_role", nullable = false)
    private AppRole teachingRole;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", insertable = false, updatable = false)
    private Course courseRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", insertable = false, updatable = false)
    private TeacherProfile teacherRef;
}
