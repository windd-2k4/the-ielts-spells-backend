package com.theieltsspells.learninglibrary.infrastructure.storage;

import com.theieltsspells.learninglibrary.domain.LearningResourceFile;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
public class FileStorageService implements FileStorage {
    private final FileStorageProperties properties;
    private final RestClient httpClient;

    public FileStorageService(FileStorageProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.httpClient = restClientBuilder.build();
    }

    @Value("${app.supabase.url:}")
    private String supabaseUrl;

    @Value("${app.supabase.service-role-key:}")
    private String supabaseServiceRoleKey;

    @Override
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
        return open(file.getStorageProvider(), file.getBucketName(), file.getObjectPath());
    }

    @Override
    public InputStream openDefault(String objectPath) {
        return open(properties.getProvider(), properties.getSupabaseBucket(), objectPath);
    }

    public InputStream open(String storageProvider, String bucketName, String objectPath) {
        return switch (storageProvider.trim().toUpperCase(Locale.ROOT)) {
            case "LOCAL" -> openLocal(objectPath);
            case "SUPABASE" -> openSupabase(bucketName, objectPath);
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
                var status = httpClient.delete()
                        .uri(objectUri(file.getBucketName(), file.getObjectPath()))
                        .header("Authorization", "Bearer " + key)
                        .header("apikey", key)
                        .exchange((request, response) -> response.getStatusCode().value());
                if (status >= 300 && status != 404) {
                    throw new BusinessRuleException("Không thể xóa tệp trên Supabase Storage");
                }
            }
        } catch (IOException | RestClientResponseException exception) {
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
            var status = httpClient.post()
                    .uri(objectUri(properties.getSupabaseBucket(), objectPath))
                    .header("Authorization", "Bearer " + key)
                    .header("apikey", key)
                    .header("x-upsert", "false")
                    .contentType(MediaType.parseMediaType(contentType(file)))
                    .body(body)
                    .exchange((request, response) -> response.getStatusCode().value());
            if (status < 200 || status >= 300) {
                throw new BusinessRuleException("Supabase Storage từ chối tải tệp lên: HTTP " + status);
            }
            return new StoredFile("SUPABASE", properties.getSupabaseBucket(), objectPath);
        } catch (IOException | RestClientResponseException exception) {
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

    private InputStream openSupabase(String bucketName, String objectPath) {
        try {
            var key = requireSupabaseKey();
            var body = httpClient.get()
                    .uri(objectUri(bucketName, objectPath))
                    .header("Authorization", "Bearer " + key)
                    .header("apikey", key)
                    .retrieve()
                    .body(byte[].class);
            if (body == null) throw new BusinessRuleException("Supabase Storage trả về tệp rỗng");
            return new ByteArrayInputStream(body);
        } catch (RestClientResponseException exception) {
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

}
