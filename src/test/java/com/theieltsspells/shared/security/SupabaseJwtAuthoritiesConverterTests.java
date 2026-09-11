package com.theieltsspells.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupabaseJwtAuthoritiesConverterTests {
    private final SupabaseJwtAuthoritiesConverter converter = new SupabaseJwtAuthoritiesConverter();

    @Test
    void convertsMultipleRolesClaim() {
        var jwt = tokenWithClaim("user_roles", List.of("admin", "teacher"));
        assertThat(converter.convert(jwt)).extracting("authority")
                .containsExactly("admin", "teacher");
    }

    @Test
    void normalizesUppercaseDatabaseRolesToCanonicalAuthorities() {
        var jwt = tokenWithClaim("user_roles", List.of("ADMIN", "SOCIAL_MEDIA", "STUDENT_SUPPORT"));
        assertThat(converter.convert(jwt)).extracting("authority")
                .containsExactly("admin", "social_media", "student_support");
    }

    @Test
    void normalizesAndDeduplicatesRolesAcrossCompatibilityClaims() {
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("user-id")
                .claim("user_roles", List.of(" ADMIN ", "teacher"))
                .claim("user_role", "admin")
                .build();

        assertThat(converter.convert(jwt)).extracting("authority")
                .containsExactly("admin", "teacher");
    }

    @Test
    void supportsSingleRoleClaimForCompatibility() {
        var jwt = tokenWithClaim("user_role", "student");
        assertThat(converter.convert(jwt)).extracting("authority")
                .containsExactly("student");
    }

    @Test
    void returnsNoAuthorityWhenRoleClaimsAreMissing() {
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("user-id").build();
        assertThat(converter.convert(jwt)).isEmpty();
    }

    private Jwt tokenWithClaim(String name, Object value) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("user-id").claim(name, value).build();
    }
}
