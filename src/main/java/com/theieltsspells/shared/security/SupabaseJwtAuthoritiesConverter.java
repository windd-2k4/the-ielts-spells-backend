package com.theieltsspells.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class SupabaseJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        var roles = new LinkedHashSet<String>();
        var multipleRoles = jwt.getClaim("user_roles");

        if (multipleRoles instanceof Collection<?> values) {
            values.stream().map(String::valueOf).map(String::trim)
                    .filter(value -> !value.isBlank()).forEach(roles::add);
        } else if (multipleRoles instanceof String value && !value.isBlank()) {
            roles.add(value.trim());
        }

        var singleRole = jwt.getClaimAsString("user_role");
        if (singleRole != null && !singleRole.isBlank()) roles.add(singleRole.trim());

        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
