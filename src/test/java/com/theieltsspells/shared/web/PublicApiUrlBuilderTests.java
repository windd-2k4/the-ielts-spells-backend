package com.theieltsspells.shared.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicApiUrlBuilderTests {
    @Test
    void usesConfiguredPublicApiBaseUrlInsteadOfInternalRequestHost() {
        var builder = new PublicApiUrlBuilder("https://api.theieltsspells.io.vn/api/v1/");

        assertThat(builder.url("/public/student-avatars/{filename}", "avatar name.png"))
                .isEqualTo("https://api.theieltsspells.io.vn/api/v1/public/student-avatars/avatar%20name.png");
    }

    @Test
    void fallsBackToStableRelativeApiPath() {
        var builder = new PublicApiUrlBuilder("");

        assertThat(builder.url("public/staff-avatars/{filename}", "avatar.png"))
                .isEqualTo("/api/v1/public/staff-avatars/avatar.png");
    }
}
