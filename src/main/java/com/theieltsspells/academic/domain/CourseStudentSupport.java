package com.theieltsspells.academic.domain;

import com.theieltsspells.identity.domain.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "course_student_supports")
@IdClass(CourseStudentSupportId.class)
@Getter
@Setter
@NoArgsConstructor
public class CourseStudentSupport {

    @Id
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Id
    @Column(name = "student_support_id", nullable = false)
    private UUID studentSupportId;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", insertable = false, updatable = false)
    private Course courseRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_support_id", insertable = false, updatable = false)
    private Profile studentSupportRef;
}
