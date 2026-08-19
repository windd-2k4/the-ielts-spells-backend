package com.theieltsspells.learninglibrary.application;

import com.theieltsspells.learninglibrary.application.dto.*;
import com.theieltsspells.learninglibrary.domain.ExerciseTemplate;
import com.theieltsspells.learninglibrary.domain.LearningResource;
import com.theieltsspells.learninglibrary.domain.LearningResourceFile;
import com.theieltsspells.learninglibrary.infrastructure.persistence.ExerciseTemplateRepository;
import com.theieltsspells.learninglibrary.infrastructure.persistence.LearningResourceFileRepository;
import com.theieltsspells.learninglibrary.infrastructure.persistence.LearningResourceRepository;
import com.theieltsspells.learninglibrary.infrastructure.storage.FileStorageService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.SkillType;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningLibraryApplicationService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> SCOPES = Set.of("GLOBAL", "COURSE");
    private static final Set<String> RESOURCE_TYPES = Set.of(
            "DOCUMENT", "AUDIO", "VIDEO", "DRIVE_LINK", "TEACHER_NOTE", "ANSWER_KEY", "VOCABULARY");
    private static final Set<String> COMPLETION_MODES = Set.of("WEB", "DRIVE_LINK", "FILE_UPLOAD", "MANUAL", "HYBRID");
    private static final Set<String> FILE_ROLES = Set.of("MAIN", "ANSWER_KEY", "TRANSCRIPT", "VOCABULARY", "AUDIO", "THUMBNAIL", "SUPPORTING");

    private final LearningResourceRepository resources;
    private final ExerciseTemplateRepository exercises;
    private final LearningResourceFileRepository resourceFiles;
    private final FileStorageService fileStorage;
    private final EntityManager entityManager;

    public ContentHubSummaryResponse summary() {
        return new ContentHubSummaryResponse(
                count("select count(*) from public.learning_resources where status <> 'ARCHIVED'"),
                count("select count(*) from public.tests where status <> 'ARCHIVED'"),
                count("select count(*) from public.learning_resource_files where archived_at is null"),
                count("select (select count(*) from public.learning_resources where status = 'DRAFT') + "
                        + "(select count(*) from public.exercise_templates where status = 'DRAFT') + "
                        + "(select count(*) from public.tests where status = 'SCHEDULED')"),
                count("select count(distinct coalesce(source_resource_id, source_exercise_template_id)) "
                        + "from public.course_session_items where source_resource_id is not null "
                        + "or source_exercise_template_id is not null")
        );
    }

    public Page<LearningResourceResponse> listResources(String query, SkillType skill, String category,
                                                        String scope, String status, UUID courseId,
                                                        boolean includeGlobal, Pageable pageable) {
        Specification<LearningResource> spec = (root, ignored, cb) -> cb.conjunction();
        if (query != null && !query.isBlank()) {
            var keyword = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), keyword), cb.like(cb.lower(root.get("code")), keyword),
                    cb.like(cb.lower(root.get("description")), keyword)));
        }
        if (skill != null) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("skill"), skill));
        if (category != null && !category.isBlank()) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("category"), category));
        if (scope != null && !scope.isBlank()) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("scope"), normalize(scope)));
        if (status != null && !status.isBlank()) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("status"), normalize(status)));
        else spec = spec.and((root, ignored, cb) -> cb.notEqual(root.get("status"), "ARCHIVED"));
        if (courseId != null) spec = spec.and((root, ignored, cb) -> includeGlobal
                ? cb.or(cb.equal(root.get("courseId"), courseId), cb.equal(root.get("scope"), "GLOBAL"))
                : cb.equal(root.get("courseId"), courseId));
        return resources.findAll(spec, pageable).map(this::resourceResponse);
    }

    public Page<ExerciseTemplateResponse> listExercises(String query, SkillType skill, String category,
                                                        String scope, String status, UUID courseId,
                                                        boolean includeGlobal, Pageable pageable) {
        Specification<ExerciseTemplate> spec = (root, ignored, cb) -> cb.conjunction();
        if (query != null && !query.isBlank()) {
            var keyword = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), keyword), cb.like(cb.lower(root.get("code")), keyword),
                    cb.like(cb.lower(root.get("instructions")), keyword)));
        }
        if (skill != null) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("skill"), skill));
        if (category != null && !category.isBlank()) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("category"), category));
        if (scope != null && !scope.isBlank()) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("scope"), normalize(scope)));
        if (status != null && !status.isBlank()) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("status"), normalize(status)));
        else spec = spec.and((root, ignored, cb) -> cb.notEqual(root.get("status"), "ARCHIVED"));
        if (courseId != null) spec = spec.and((root, ignored, cb) -> includeGlobal
                ? cb.or(cb.equal(root.get("courseId"), courseId), cb.equal(root.get("scope"), "GLOBAL"))
                : cb.equal(root.get("courseId"), courseId));
        return exercises.findAll(spec, pageable).map(this::exerciseResponse);
    }

    public LearningResourceResponse getResource(UUID id) { return resourceResponse(findResource(id)); }
    public ExerciseTemplateResponse getExercise(UUID id) { return exerciseResponse(findExercise(id)); }

    public java.util.List<LearningResourceFileResponse> listResourceFiles(UUID resourceId) {
        findResource(resourceId);
        return resourceFiles.findByResourceIdAndArchivedAtIsNullOrderByCreatedAtAsc(resourceId).stream()
                .map(this::fileResponse).toList();
    }

    @Transactional
    public LearningResourceResponse createResource(LearningResourceRequest request, UUID actor) {
        var value = new LearningResource();
        value.setCreatedBy(actor);
        apply(value, request);
        value = resources.saveAndFlush(value);
        entityManager.refresh(value);
        return resourceResponse(value);
    }

    @Transactional
    public LearningResourceResponse updateResource(UUID id, LearningResourceRequest request) {
        var value = findResource(id);
        apply(value, request);
        return resourceResponse(resources.save(value));
    }

    @Transactional
    public void archiveResource(UUID id) { findResource(id).setStatus("ARCHIVED"); }

    @Transactional
    public LearningResourceFileResponse uploadResourceFile(UUID resourceId, String fileRole, MultipartFile file, UUID actor) {
        findResource(resourceId);
        var originalFilename = normalizedFilename(file.getOriginalFilename());
        var objectPath = "resources/" + resourceId + "/" + UUID.randomUUID() + "-" + originalFilename;
        var stored = fileStorage.store(objectPath, file);
        var value = new LearningResourceFile();
        value.setResourceId(resourceId);
        value.setFileRole(allowed(fileRole == null ? "MAIN" : fileRole, FILE_ROLES, "Vai trò tệp không hợp lệ"));
        value.setStorageProvider(stored.provider());
        value.setBucketName(stored.bucketName());
        value.setObjectPath(stored.objectPath());
        value.setOriginalFilename(originalFilename);
        value.setMimeType(file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream" : file.getContentType());
        value.setSizeBytes(file.getSize());
        value.setUploadedBy(actor);
        return fileResponse(resourceFiles.save(value));
    }

    @Transactional
    public void deleteResourceFile(UUID resourceId, UUID fileId) {
        findResource(resourceId);
        var file = findFile(fileId);
        if (!resourceId.equals(file.getResourceId())) throw new ResourceNotFoundException("Không tìm thấy tệp trong học liệu này");
        fileStorage.delete(file);
        resourceFiles.delete(file);
    }

    public FileContent openResourceFile(UUID fileId) {
        var file = findFile(fileId);
        if (file.getArchivedAt() != null) throw new ResourceNotFoundException("Tệp đã bị lưu trữ");
        return new FileContent(file.getOriginalFilename(), file.getMimeType(), file.getSizeBytes(), fileStorage.open(file));
    }

    public Page<MediaAssetResponse> listMedia(String query, String type, Pageable pageable) {
        Specification<LearningResourceFile> spec = (root, ignored, cb) -> cb.isNull(root.get("archivedAt"));
        if (query != null && !query.isBlank()) {
            var keyword = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, ignored, cb) -> cb.like(cb.lower(root.get("originalFilename")), keyword));
        }
        if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
            var normalized = type.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, ignored, cb) -> switch (normalized) {
                case "AUDIO" -> cb.like(cb.lower(root.get("mimeType")), "audio/%");
                case "IMAGE" -> cb.like(cb.lower(root.get("mimeType")), "image/%");
                case "PDF" -> cb.equal(cb.lower(root.get("mimeType")), "application/pdf");
                case "DOCUMENT" -> cb.and(cb.notLike(cb.lower(root.get("mimeType")), "audio/%"),
                        cb.notLike(cb.lower(root.get("mimeType")), "image/%"),
                        cb.notEqual(cb.lower(root.get("mimeType")), "application/pdf"));
                default -> throw new BusinessRuleException("Loại media không hợp lệ");
            });
        }
        return resourceFiles.findAll(spec, pageable).map(this::mediaResponse);
    }

    @Transactional
    public MediaAssetResponse uploadMedia(MultipartFile file, UUID actor) {
        var resource = new LearningResource();
        resource.setCreatedBy(actor);
        resource.setTitle(normalizedFilename(file.getOriginalFilename()));
        resource.setSkill(SkillType.GENERAL);
        resource.setCategory("MEDIA");
        resource.setResourceType(resourceType(file.getContentType()));
        resource.setScope("GLOBAL");
        resource.setCourseId(null);
        resource.setExternalUrl(null);
        resource.setTeacherOnly(false);
        resource.setStatus("PUBLISHED");
        resource = resources.saveAndFlush(resource);
        entityManager.refresh(resource);
        uploadResourceFile(resource.getId(), "MAIN", file, actor);
        var stored = resourceFiles.findByResourceIdAndArchivedAtIsNullOrderByCreatedAtAsc(resource.getId()).getFirst();
        return mediaResponse(stored);
    }

    @Transactional
    public void deleteMedia(UUID fileId) {
        var file = findFile(fileId);
        var count = entityManager.createNativeQuery("select count(*) from public.course_session_items where source_resource_id=:id")
                .setParameter("id", file.getResourceId()).getSingleResult();
        if (((Number) count).longValue() > 0) throw new BusinessRuleException("File đang được gắn vào buổi học; hãy gỡ liên kết trước khi xóa");
        deleteResourceFile(file.getResourceId(), fileId);
    }

    @Transactional
    public ExerciseTemplateResponse createExercise(ExerciseTemplateRequest request, UUID actor) {
        var value = new ExerciseTemplate();
        value.setCreatedBy(actor);
        apply(value, request);
        value = exercises.saveAndFlush(value);
        entityManager.refresh(value);
        return exerciseResponse(value);
    }

    @Transactional
    public ExerciseTemplateResponse updateExercise(UUID id, ExerciseTemplateRequest request) {
        var value = findExercise(id);
        apply(value, request);
        return exerciseResponse(exercises.save(value));
    }

    @Transactional
    public void archiveExercise(UUID id) { findExercise(id).setStatus("ARCHIVED"); }

    private void apply(LearningResource value, LearningResourceRequest request) {
        var scope = allowed(request.scope(), SCOPES, "Phạm vi tài liệu không hợp lệ");
        validateCourseScope(scope, request.courseId());
        validateUrl(request.externalUrl(), false);
        value.setTitle(request.title().trim()); value.setDescription(blank(request.description()));
        value.setSkill(request.skill()); value.setCategory(request.category().trim());
        value.setResourceType(allowed(request.resourceType(), RESOURCE_TYPES, "Loại tài liệu không hợp lệ"));
        value.setScope(scope); value.setCourseId("COURSE".equals(scope) ? request.courseId() : null);
        value.setExternalUrl(blank(request.externalUrl()));
        value.setTeacherOnly(Boolean.TRUE.equals(request.teacherOnly()) || "TEACHER_NOTE".equals(value.getResourceType()));
        value.setStatus(allowed(request.status(), STATUSES, "Trạng thái tài liệu không hợp lệ"));
    }

    private void apply(ExerciseTemplate value, ExerciseTemplateRequest request) {
        var scope = allowed(request.scope(), SCOPES, "Phạm vi bài tập không hợp lệ");
        validateCourseScope(scope, request.courseId());
        validateUrl(request.sourceUrl(), false);
        value.setTitle(request.title().trim()); value.setInstructions(blank(request.instructions()));
        value.setSkill(request.skill()); value.setCategory(request.category().trim());
        value.setExerciseType(request.exerciseType().trim());
        value.setCompletionMode(allowed(request.completionMode(), COMPLETION_MODES, "Phương thức hoàn thành không hợp lệ"));
        value.setScope(scope); value.setCourseId("COURSE".equals(scope) ? request.courseId() : null);
        value.setSourceUrl(blank(request.sourceUrl())); value.setDurationMinutes(request.durationMinutes());
        value.setMaxScore(request.maxScore()); value.setAttemptLimit(request.attemptLimit() == null ? (short) 1 : request.attemptLimit());
        value.setRequiresTeacherReview(Boolean.TRUE.equals(request.requiresTeacherReview()));
        value.setContent(request.content() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.content()));
        value.setAnswerKey(request.answerKey() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.answerKey()));
        value.setStatus(allowed(request.status(), STATUSES, "Trạng thái bài tập không hợp lệ"));
    }

    private void validateCourseScope(String scope, UUID courseId) {
        if ("COURSE".equals(scope) && courseId == null) throw new BusinessRuleException("Tài nguyên theo khóa học cần chọn khóa học");
        if (courseId != null) {
            var count = entityManager.createNativeQuery("select count(*) from public.courses where id = :id")
                    .setParameter("id", courseId).getSingleResult();
            if (((Number) count).longValue() == 0) throw new ResourceNotFoundException("Không tìm thấy khóa học");
        }
    }

    private void validateUrl(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new BusinessRuleException("Cần nhập đường dẫn tài liệu");
            return;
        }
        try {
            var uri = URI.create(value.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Đường dẫn phải bắt đầu bằng http:// hoặc https://");
        }
    }

    private String allowed(String value, Set<String> allowed, String message) {
        var normalized = normalize(value);
        if (!allowed.contains(normalized)) throw new BusinessRuleException(message);
        return normalized;
    }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private LearningResource findResource(UUID id) { return resources.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu")); }
    private ExerciseTemplate findExercise(UUID id) { return exercises.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập mẫu")); }
    private LearningResourceFile findFile(UUID id) { return resourceFiles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tệp học liệu")); }
    private LearningResourceResponse resourceResponse(LearningResource value) {
        return new LearningResourceResponse(value.getId(), value.getCode(), value.getTitle(), value.getDescription(), value.getSkill(),
                value.getCategory(), value.getResourceType(), value.getScope(), value.getCourseId(), value.getExternalUrl(),
                Boolean.TRUE.equals(value.getTeacherOnly()), value.getStatus(), value.getCreatedBy(), value.getCreatedAt(), value.getUpdatedAt());
    }
    private ExerciseTemplateResponse exerciseResponse(ExerciseTemplate value) {
        return new ExerciseTemplateResponse(value.getId(), value.getCode(), value.getTitle(), value.getInstructions(), value.getSkill(),
                value.getCategory(), value.getExerciseType(), value.getCompletionMode(), value.getScope(), value.getCourseId(),
                value.getSourceUrl(), value.getDurationMinutes(), value.getMaxScore(), value.getAttemptLimit(),
                Boolean.TRUE.equals(value.getRequiresTeacherReview()), value.getContent(), value.getAnswerKey(), value.getStatus(),
                value.getCreatedBy(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private LearningResourceFileResponse fileResponse(LearningResourceFile value) {
        var mime = value.getMimeType().toLowerCase(Locale.ROOT);
        var previewSupported = mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("video/")
                || "application/pdf".equals(mime) || mime.startsWith("text/");
        return new LearningResourceFileResponse(value.getId(), value.getResourceId(), value.getFileRole(),
                value.getOriginalFilename(), value.getMimeType(), value.getSizeBytes(), previewSupported, value.getCreatedAt());
    }

    @SuppressWarnings("unchecked")
    private MediaAssetResponse mediaResponse(LearningResourceFile value) {
        var rows = entityManager.createNativeQuery("""
                select cs.id, coalesce(cs.title, 'Buổi học')
                from public.course_session_items csi
                join public.course_sessions cs on cs.id=csi.session_id
                where csi.source_resource_id=:resourceId order by cs.starts_at
                """).setParameter("resourceId", value.getResourceId()).getResultList();
        var locations = ((List<Object[]>) rows).stream().map(row -> new MediaAssetResponse.UsageLocation(
                "LESSON", String.valueOf(row[1]), (UUID) row[0])).toList();
        String uploader = "Không xác định";
        if (value.getUploadedBy() != null) {
            var names = entityManager.createNativeQuery("select coalesce(full_name,email,'Không xác định') from public.profiles where id=:id")
                    .setParameter("id", value.getUploadedBy()).getResultList();
            if (!names.isEmpty()) uploader = String.valueOf(names.getFirst());
        }
        return new MediaAssetResponse(value.getId(), value.getResourceId(), "MED-" + value.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                value.getOriginalFilename(), mediaType(value.getMimeType()), "/admin/library/files/" + value.getId() + "/content",
                value.getSizeBytes(), List.of(), locations, uploader, value.getCreatedAt());
    }

    private String mediaType(String mimeType) {
        var mime = mimeType.toLowerCase(Locale.ROOT);
        if (mime.startsWith("audio/")) return "AUDIO";
        if (mime.startsWith("image/")) return "IMAGE";
        if ("application/pdf".equals(mime)) return "PDF";
        return "DOCUMENT";
    }

    private String resourceType(String mimeType) {
        if (mimeType == null) return "DOCUMENT";
        var mime = mimeType.toLowerCase(Locale.ROOT);
        if (mime.startsWith("audio/")) return "AUDIO";
        if (mime.startsWith("video/")) return "VIDEO";
        return "DOCUMENT";
    }

    private String normalizedFilename(String originalFilename) {
        var source = originalFilename == null || originalFilename.isBlank() ? "untitled-file" : originalFilename;
        var normalized = source.replaceAll("[^a-zA-Z0-9._-]", "-").replaceAll("-+", "-");
        return normalized.length() > 180 ? normalized.substring(normalized.length() - 180) : normalized;
    }

    private long count(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }

    public record FileContent(String originalFilename, String mimeType, long sizeBytes, InputStream inputStream) {}
}
