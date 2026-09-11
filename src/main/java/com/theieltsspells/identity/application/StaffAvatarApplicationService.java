package com.theieltsspells.identity.application;

import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class StaffAvatarApplicationService {
    private static final long MAX_AVATAR_BYTES = 5 * 1024 * 1024;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, "image/webp", "image/gif");
    private static final Pattern FILENAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp|gif)$");
    private static final String AVATAR_DIRECTORY = "staff/avatars/";

    private final FileStorage storage;

    public String upload(MultipartFile file) {
        validateImage(file);
        String filename = UUID.randomUUID() + extension(file.getContentType());
        storage.store(AVATAR_DIRECTORY + filename, file);
        return filename;
    }

    public AvatarContent open(String filename) {
        if (filename == null || !FILENAME.matcher(filename).matches()) {
            throw new BusinessRuleException("Ảnh đại diện không hợp lệ");
        }
        InputStream stream = storage.openDefault(AVATAR_DIRECTORY + filename);
        return new AvatarContent(stream, mediaType(filename));
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Vui lòng chọn ảnh đại diện");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BusinessRuleException("Ảnh đại diện không được vượt quá 5 MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(contentType)) {
            throw new BusinessRuleException("Ảnh đại diện chỉ hỗ trợ PNG, JPG, WebP hoặc GIF");
        }
    }

    private String extension(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case MediaType.IMAGE_PNG_VALUE -> ".png";
            case MediaType.IMAGE_JPEG_VALUE -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> throw new BusinessRuleException("Định dạng ảnh không hợp lệ");
        };
    }

    private MediaType mediaType(String filename) {
        if (filename.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (filename.endsWith(".jpg")) return MediaType.IMAGE_JPEG;
        if (filename.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_GIF;
    }

    public record AvatarContent(InputStream inputStream, MediaType mediaType) {}
}
