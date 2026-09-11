package com.theieltsspells.identity.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseAdminClientTests {

    private static final String SUPABASE_URL = "https://project.supabase.co";
    private static final String SERVICE_ROLE_KEY = "test-service-role-key";

    private MockRestServiceServer server;
    private SupabaseAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SupabaseAdminClient(builder, SUPABASE_URL, SERVICE_ROLE_KEY,
                "http://localhost:5174");
    }

    @Test
    void assignsRoleUsingTheCompositeKeyColumnsWhenRoleIsMissing() {
        UUID userId = UUID.fromString("7f14e018-ff88-4d2c-99ad-094bc593f54e");
        String lookupUrl = SUPABASE_URL + "/rest/v1/user_roles"
                + "?select=user_id&user_id=eq." + userId + "&role=eq.TEACHER&limit=1";

        server.expect(once(), requestTo(lookupUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(SUPABASE_URL + "/rest/v1/user_roles"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "user_id": "7f14e018-ff88-4d2c-99ad-094bc593f54e",
                          "role": "TEACHER"
                        }
                        """))
                .andRespond(withSuccess());

        client.assignRole(userId, "TEACHER", null, OffsetDateTime.now());

        server.verify();
    }

    @Test
    void skipsInsertWhenCompositeRoleAlreadyExists() {
        UUID userId = UUID.fromString("7f14e018-ff88-4d2c-99ad-094bc593f54e");
        String lookupUrl = SUPABASE_URL + "/rest/v1/user_roles"
                + "?select=user_id&user_id=eq." + userId + "&role=eq.TEACHER&limit=1";

        server.expect(once(), requestTo(lookupUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"user_id":"7f14e018-ff88-4d2c-99ad-094bc593f54e"}]
                        """, MediaType.APPLICATION_JSON));

        client.assignRole(userId, "TEACHER", null, OffsetDateTime.now());

        server.verify();
    }

    @Test
    void upsertsRemoteProfileRequiredByTheUserRoleForeignKey() {
        UUID userId = UUID.fromString("7f14e018-ff88-4d2c-99ad-094bc593f54e");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-09-10T17:45:01+07:00");

        server.expect(once(), requestTo(SUPABASE_URL + "/rest/v1/profiles?on_conflict=id"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Prefer", "resolution=merge-duplicates,return=minimal"))
                .andExpect(content().json("""
                        {
                          "id": "7f14e018-ff88-4d2c-99ad-094bc593f54e",
                          "full_name": "Nguyen Van A",
                          "email": "teacher@example.com",
                          "phone": null,
                          "avatar_path": null,
                          "is_active": true,
                          "updated_at": "2026-09-10T17:45:01+07:00"
                        }
                        """))
                .andRespond(withSuccess());

        client.upsertProfile(userId, "Nguyen Van A", "teacher@example.com", null, null, updatedAt);

        server.verify();
    }
}
