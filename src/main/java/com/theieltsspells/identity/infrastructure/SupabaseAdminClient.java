package com.theieltsspells.identity.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.theieltsspells.shared.application.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class SupabaseAdminClient {
    private final RestClient client;
    private final String key;
    private final String managementUrl;

    public SupabaseAdminClient(RestClient.Builder builder,
                               @Value("${app.supabase.url:}") String url,
                               @Value("${app.supabase.service-role-key:}") String key,
                               @Value("${app.access-approval.management-url}") String managementUrl) {
        this.client = builder.baseUrl(url.isBlank() ? "https://invalid.local" : url).build();
        this.key = key;
        this.managementUrl = managementUrl;
    }

    public UUID findOrInvite(String email, String name) {
        requireConfiguration();
        try {
            JsonNode result = client.get()
                    .uri(uri -> uri.path("/auth/v1/admin/users")
                            .queryParam("page", 1).queryParam("per_page", 1000).build())
                    .headers(this::authorize)
                    .retrieve().body(JsonNode.class);
            if (result != null) {
                for (JsonNode user : result.path("users")) {
                    if (email.equalsIgnoreCase(user.path("email").asText()))
                        return UUID.fromString(user.path("id").asText());
                }
            }
            return invite(email, name);
        } catch (RestClientResponseException exception) {
            throw new BusinessRuleException("Supabase Auth từ chối yêu cầu quản trị (HTTP "
                    + exception.getStatusCode().value() + ")");
        }
    }

    public UUID inviteStaff(String email, String name) {
        requireConfiguration();
        try {
            return invite(email, name);
        } catch (RestClientResponseException exception) {
            throw new BusinessRuleException("Không thể gửi lời mời qua Supabase (HTTP "
                    + exception.getStatusCode().value() + "): " + exception.getResponseBodyAsString());
        }
    }

    public void assignRole(UUID userId, String role, UUID assignedBy, OffsetDateTime assignedAt) {
        requireConfiguration();
        try {
            JsonNode existing = client.get()
                    .uri(uri -> uri.path("/rest/v1/user_roles")
                            .queryParam("select", "user_id")
                            .queryParam("user_id", "eq." + userId)
                            .queryParam("role", "eq." + role)
                            .queryParam("limit", 1)
                            .build())
                    .headers(this::authorize)
                    .retrieve().body(JsonNode.class);
            if (existing != null && existing.isArray() && !existing.isEmpty()) return;

            client.post().uri("/rest/v1/user_roles")
                    .headers(this::authorize)
                    .contentType(MediaType.APPLICATION_JSON)
                    // Cloud currently uses an identity id and only requires
                    // user_id/role. Audit fields remain in the local database.
                    .body(Map.of("user_id", userId.toString(), "role", role))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new BusinessRuleException("Không thể lưu vai trò lên Supabase (HTTP "
                    + exception.getStatusCode().value() + "): " + exception.getResponseBodyAsString());
        }
    }

    public void upsertProfile(UUID userId, String fullName, String email, String phone,
                              String avatarPath, OffsetDateTime updatedAt) {
        requireConfiguration();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", userId.toString());
        body.put("full_name", fullName);
        body.put("email", email);
        body.put("phone", phone);
        body.put("avatar_path", avatarPath);
        body.put("is_active", true);
        body.put("updated_at", updatedAt.toString());

        try {
            client.post()
                    .uri(uri -> uri.path("/rest/v1/profiles")
                            .queryParam("on_conflict", "id")
                            .build())
                    .headers(headers -> {
                        authorize(headers);
                        headers.set("Prefer", "resolution=merge-duplicates,return=minimal");
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new BusinessRuleException("Không thể lưu hồ sơ lên Supabase (HTTP "
                    + exception.getStatusCode().value() + "): " + exception.getResponseBodyAsString());
        }
    }

    public void replaceRole(UUID userId, String previousRole, String nextRole) {
        requireConfiguration();
        if (previousRole.equals(nextRole)) return;
        try {
            // Grant first so a transient Supabase failure never leaves the user without a role.
            assignRole(userId, nextRole, null, OffsetDateTime.now());
            client.delete()
                    .uri(uri -> uri.path("/rest/v1/user_roles")
                            .queryParam("user_id", "eq." + userId)
                            .queryParam("role", "eq." + previousRole)
                            .build())
                    .headers(this::authorize)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new BusinessRuleException("Không thể đồng bộ vai trò với Supabase (HTTP "
                    + exception.getStatusCode().value() + "): " + exception.getResponseBodyAsString());
        }
    }

    public void setUserSuspended(UUID userId, boolean suspended) {
        requireConfiguration();
        try {
            client.put().uri("/auth/v1/admin/users/{id}", userId)
                    .headers(this::authorize)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ban_duration", suspended ? "876000h" : "none"))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new BusinessRuleException("Không thể cập nhật trạng thái Supabase Auth (HTTP "
                    + exception.getStatusCode().value() + "): " + exception.getResponseBodyAsString());
        }
    }

    private UUID invite(String email, String name) {
        JsonNode body = client.post()
                .uri(uri -> uri.path("/auth/v1/invite")
                        .queryParam("redirect_to", managementUrl + "/auth/callback")
                        .build())
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "data", Map.of("full_name", name)))
                .retrieve().body(JsonNode.class);
        if (body == null || body.path("id").isMissingNode())
            throw new BusinessRuleException("Supabase không trả về người dùng sau khi gửi lời mời");
        return UUID.fromString(body.path("id").asText());
    }

    private void authorize(org.springframework.http.HttpHeaders headers) {
        headers.set("apikey", key);
        headers.setBearerAuth(key);
    }

    private void requireConfiguration() {
        if (key.isBlank()) throw new BusinessRuleException("Chưa cấu hình SUPABASE_SERVICE_ROLE_KEY");
    }
}
