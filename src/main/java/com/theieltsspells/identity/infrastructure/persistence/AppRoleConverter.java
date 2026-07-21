package com.theieltsspells.identity.infrastructure.persistence;

import com.theieltsspells.shared.persistence.enums.AppRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

@Converter
public class AppRoleConverter implements AttributeConverter<AppRole, String> {
    @Override public String convertToDatabaseColumn(AppRole role) { return role == null ? null : role.getDatabaseValue(); }
    @Override public AppRole convertToEntityAttribute(String value) {
        if (value == null) return null;
        return Arrays.stream(AppRole.values()).filter(role -> role.getDatabaseValue().equals(value))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown app role: " + value));
    }
}
