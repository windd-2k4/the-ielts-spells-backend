package com.theieltsspells.learninglibrary.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "learning_resource_files")
@Getter
@Setter
@NoArgsConstructor
public class LearningResourceFile {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "file_role", nullable = false)
    private String fileRole;

    @Column(name = "storage_provider", nullable = false)
    private String storageProvider;

    @Column(name = "bucket_name")
    private String bucketName;

    @Column(name = "object_path", nullable = false)
    private String objectPath;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256")
    private String checksumSha256;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;
}
