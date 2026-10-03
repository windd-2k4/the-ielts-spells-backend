package com.theieltsspells.testing.application.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.persistence.enums.SkillType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestBankSummaryResponseTest {

    @Test
    void summaryContractDoesNotExposeBuilderContent() {
        var summary = new TestBankSummaryResponse(
                UUID.randomUUID(), "TEST-001", "Reading test", null,
                SkillType.READING, "SINGLE_SKILL", "PASSAGE_1", 1, 13,
                20, "v1.0", "DRAFT", List.of("PASSAGE_1"), List.of("NOTE_COMPLETION"),
                Map.of(), Map.of(), 0, "Admin", null, null, 1, null);

        var json = new ObjectMapper().valueToTree(summary);

        assertThat(json.has("builderContent")).isFalse();
        assertThat(json.get("totalQuestions").asInt()).isEqualTo(13);
        assertThat(json.get("format").asText()).isEqualTo("PASSAGE_1");
    }
}
