package com.theieltsspells.identity.infrastructure.persistence;

import com.theieltsspells.shared.persistence.enums.AppRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppRoleConverterTests {

    private final AppRoleConverter converter = new AppRoleConverter();

    @Test
    void readsUppercasePostgresqlEnumLabel() {
        assertThat(converter.convertToEntityAttribute("TEACHER")).isEqualTo(AppRole.TEACHER);
    }

    @Test
    void remainsCompatibleWithLegacyLowercaseLabel() {
        assertThat(converter.convertToEntityAttribute("teaching_assistant"))
                .isEqualTo(AppRole.TEACHING_ASSISTANT);
    }

    @Test
    void writesLabelExpectedByCurrentPostgresqlEnum() {
        assertThat(converter.convertToDatabaseColumn(AppRole.ADMIN)).isEqualTo("ADMIN");
    }
}
