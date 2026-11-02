package com.theieltsspells.adaptivelearning.application.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpsertAdaptiveContentMappingRequestTests {
    @Test
    void rejectsInvalidTargetAndHref() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new UpsertAdaptiveContentMappingRequest(
                    "reading.main-idea", "TEST_VERSION", "not-a-uuid",
                    "Matching Headings", "student/practice", 15, "MEDIUM", 0, true
            ));

            assertThat(violations).extracting(value -> value.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("targetId", "href");
        }
    }
}
