package com.theieltsspells.learninglibrary.infrastructure.storage;

import com.theieltsspells.learninglibrary.domain.LearningResourceFile;
import com.theieltsspells.shared.application.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FileStorageService {
    private final FileStorageProperties properties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.supabase.url:}")
    private String supabaseUrl;

    @Value("${app.supabase.service-role-key:}")
    private String supabaseServiceRoleKey;

    public StoredFile store(String objectPath, MultipartFile file) {
        validate(file);
        var provider = properties.getProvider().trim().toUpperCase(Locale.ROOT);
        return switch (provider) {
            case "LOCAL" -> storeLocal(objectPath, file);
            case "SUPABASE" -> storeSupabase(objectPath, file);
            default -> throw new BusinessRuleException("FILE_STORAGE_PROVIDER chỉ hỗ trợ LOCAL hoặc SUPABASE");
        };
    }

    public InputStream open(LearningResourceFile file) {
        return switch (file.getStorageProvider()) {
            case "LOCAL" -> openLocal(file.getObjectPath());
            case "SUPABASE" -> openSupabase(file);
            default -> throw new BusinessRuleException("Không nhận diện được nơi lưu tệp");
        };
    }

    public void delete(LearningResourceFile file) {
        try {
            if ("LOCAL".equals(file.getStorageProvider())) {
                Files.deleteIfExists(localPath(file.getObjectPath()));
                return;
            }
            if ("SUPABASE".equals(file.getStorageProvider())) {
                var key = requireSupabaseKey();
                var request = HttpRequest.newBuilder(objectUri(file.getBucketName(), file.getObjectPath()))
                        .header("Authorization", "Bearer " + key)
                        .header("apikey", key)
                        .DELETE().build();
                var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 300 && response.statusCode() != 404) {
                    throw new BusinessRuleException("Không thể xóa tệp trên Supabase Storage");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("Không thể xóa tệp lưu trữ");
        } catch (IOException exception) {
            throw new BusinessRuleException("Không thể xóa tệp lưu trữ");
        }
    }

    private StoredFile storeLocal(String objectPath, MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            var target = localPath(objectPath);
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile("LOCAL", null, objectPath);
        } catch (IOException exception) {
            throw new BusinessRuleException("Không thể lưu tệp trên máy phát triển");
        }
    }

    private StoredFile storeSupabase(String objectPath, MultipartFile file) {
        try {
            var body = file.getBytes();
            var key = requireSupabaseKey();
            var request = HttpRequest.newBuilder(objectUri(properties.getSupabaseBucket(), objectPath))
                    .header("Authorization", "Bearer " + key)
                    .header("apikey", key)
                    .header("x-upsert", "false")
                    .header("Content-Type", contentType(file))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessRuleException("Supabase Storage từ chối tải tệp lên: HTTP " + response.statusCode());
            }
            return new StoredFile("SUPABASE", properties.getSupabaseBucket(), objectPath);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("Không thể tải tệp lên Supabase Storage");
        } catch (IOException exception) {
            throw new BusinessRuleException("Không thể tải tệp lên Supabase Storage");
        }
    }

    private InputStream openLocal(String objectPath) {
        try {
            return Files.newInputStream(localPath(objectPath));
        } catch (IOException exception) {
            throw new BusinessRuleException("Tệp không còn tồn tại trong kho local");
        }
    }

    private InputStream openSupabase(LearningResourceFile file) {
        try {
            var key = requireSupabaseKey();
            var request = HttpRequest.newBuilder(objectUri(file.getBucketName(), file.getObjectPath()))
                    .header("Authorization", "Bearer " + key)
                    .header("apikey", key)
                    .GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new BusinessRuleException("Không thể đọc tệp từ Supabase Storage");
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("Không thể tải tệp từ Supabase Storage");
        } catch (IOException exception) {
            throw new BusinessRuleException("Không thể tải tệp từ Supabase Storage");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessRuleException("Vui lòng chọn một tệp để tải lên");
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new BusinessRuleException("Tệp vượt quá dung lượng tối đa " + (properties.getMaxUploadBytes() / 1_048_576) + " MB");
        }
    }

    private Path localPath(String objectPath) {
        var root = Path.of(properties.getLocalDirectory()).toAbsolutePath().normalize();
        var target = root.resolve(objectPath).normalize();
        if (!target.startsWith(root)) throw new BusinessRuleException("Đường dẫn tệp không hợp lệ");
        return target;
    }

    private URI objectUri(String bucket, String objectPath) {
        if (supabaseUrl == null || supabaseUrl.isBlank()) throw new BusinessRuleException("Chưa cấu hình SUPABASE_URL cho Storage");
        return URI.create(supabaseUrl.replaceAll("/$", "") + "/storage/v1/object/" + bucket + "/" + objectPath);
    }

    private String requireSupabaseKey() {
        if (supabaseServiceRoleKey == null || supabaseServiceRoleKey.isBlank()) {
            throw new BusinessRuleException("Chưa cấu hình SUPABASE_SERVICE_ROLE_KEY cho Storage");
        }
        return supabaseServiceRoleKey;
    }

    private String contentType(MultipartFile file) {
        return file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream" : file.getContentType();
    }

    public record StoredFile(String provider, String bucketName, String objectPath) {}
}
