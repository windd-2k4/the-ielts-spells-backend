package com.theieltsspells.cms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "cms_page_revisions",
        uniqueConstraints = @UniqueConstraint(
                name = "cms_page_revisions_page_revision_unique",
                columnNames = {"page_id", "revision_no"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class CmsPageRevision {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "page_id", nullable = false)
    private UUID pageId;

    @Column(name = "revision_no", nullable = false)
    private long revisionNo;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "excerpt")
    private String excerpt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false)
    private Map<String, Object> content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seo_metadata", nullable = false)
    private Map<String, Object> seoMetadata;

    @Column(name = "source", nullable = false, length = 24)
    private String source;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
