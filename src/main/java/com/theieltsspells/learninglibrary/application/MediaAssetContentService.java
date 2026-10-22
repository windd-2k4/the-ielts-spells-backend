package com.theieltsspells.learninglibrary.application;

import com.theieltsspells.learninglibrary.MediaAssetContentReader;
import com.theieltsspells.learninglibrary.infrastructure.persistence.LearningResourceFileRepository;
import com.theieltsspells.learninglibrary.infrastructure.storage.FileStorageService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaAssetContentService implements MediaAssetContentReader {
    private static final Set<String> AI_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final LearningResourceFileRepository files;
    private final FileStorageService storage;

    @Override
    public ImageContent readImage(UUID fileId, long maxBytes) {
        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE - 1L) {
            throw new IllegalArgumentException("Invalid image size limit");
        }
        var file = files.findById(fileId)
                .filter(value -> value.getArchivedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh đề Writing"));
        String mimeType = file.getMimeType().toLowerCase(java.util.Locale.ROOT);
        if (!AI_IMAGE_TYPES.contains(mimeType)) {
            throw new BusinessRuleException("Ảnh đề Writing phải là PNG, JPG hoặc WebP để AI chấm Task 1");
        }
        if (file.getSizeBytes() > maxBytes) {
            throw new BusinessRuleException("Ảnh đề Writing vượt quá dung lượng AI cho phép");
        }
        try (var input = storage.open(file)) {
            byte[] data = input.readNBytes((int) maxBytes + 1);
            if (data.length == 0 || data.length > maxBytes) {
                throw new BusinessRuleException("Ảnh đề Writing rỗng hoặc vượt quá dung lượng AI cho phép");
            }
            return new ImageContent(mimeType, data);
        } catch (IOException exception) {
            throw new BusinessRuleException("Không thể đọc ảnh đề Writing");
        }
    }
}
