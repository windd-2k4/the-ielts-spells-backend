package com.theieltsspells.learninglibrary.domain;

import com.theieltsspells.shared.persistence.enums.SkillType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "learning_resources")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class LearningResource {
    @Id @GeneratedValue @UuidGenerator private UUID id;
    @Column(insertable = false, updatable = false) private String code;
    @Column(nullable = false) private String title;
    private String description;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(nullable = false) private SkillType skill;
    @Column(nullable = false) private String category;
    @Column(name = "resource_type", nullable = false) private String resourceType;
    @Column(nullable = false) private String scope;
    @Column(name = "course_id") private UUID courseId;
    @Column(name = "external_url") private String externalUrl;
    @Column(name = "teacher_only", nullable = false) private Boolean teacherOnly;
    @Column(nullable = false) private String status;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private OffsetDateTime updatedAt;
}
