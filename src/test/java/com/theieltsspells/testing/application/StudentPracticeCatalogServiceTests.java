package com.theieltsspells.testing.application;

import com.theieltsspells.shared.persistence.enums.SkillType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudentPracticeCatalogServiceTests {

    private final StudentPracticeCatalogService service = new StudentPracticeCatalogService(null, null);

    @Test
    void usesWritingTaskOneImageAsCatalogCover() {
        Map<String, Object> content = Map.of(
                "coverImage", Map.of("fileUrl", "/media/old-cover"),
                "tasks", List.of(Map.of(
                        "taskNo", 1,
                        "imageUrl", "/media/writing-chart",
                        "imageAltText", "Biểu đồ Writing Task 1"
                ))
        );

        assertThat(service.catalogCover(SkillType.WRITING, content))
                .containsEntry("fileUrl", "/media/writing-chart")
                .containsEntry("altText", "Biểu đồ Writing Task 1");
    }

    @Test
    void keepsConfiguredCoverForOtherSkills() {
        Map<String, Object> cover = Map.of("fileUrl", "/media/reading-cover", "altText", "Reading");

        assertThat(service.catalogCover(SkillType.READING, Map.of("coverImage", cover)))
                .isEqualTo(cover);
    }

    @Test
    void buildsCatalogDescriptionsFromPublishedTestContent() {
        assertThat(service.catalogDescription(SkillType.READING, Map.of(
                "passages", List.of(Map.of("content", "<h2>Advantages and disadvantages</h2><p>Nowadays it is easy to apply for credit.</p>"))
        ))).isEqualTo("Advantages and disadvantages Nowadays it is easy to apply for credit.");

        assertThat(service.catalogDescription(SkillType.LISTENING, Map.of(
                "parts", List.of(Map.of("questionGroups", List.of(Map.of(
                        "instructions", "Choose the correct answer.",
                        "questions", List.of(Map.of("prompt", "Why is the caller moving house?"))
                ))))
        ))).isEqualTo("Why is the caller moving house?");

        assertThat(service.catalogDescription(SkillType.WRITING, Map.of(
                "tasks", List.of(Map.of("promptHtml", "<p>Discuss both views and give your own opinion.</p>"))
        ))).isEqualTo("Discuss both views and give your own opinion.");

        assertThat(service.catalogDescription(SkillType.SPEAKING, Map.of(
                "parts", List.of(Map.of("questions", List.of(Map.of("promptText", "Where is your hometown?"))))
        ))).isEqualTo("Where is your hometown?");
    }

    @Test
    void keepsCatalogDescriptionCompact() {
        String longPassage = "A detailed IELTS passage about education and technology ".repeat(8);

        assertThat(service.catalogDescription(SkillType.READING, Map.of(
                "passages", List.of(Map.of("content", longPassage))
        )))
                .endsWith("…")
                .hasSizeLessThanOrEqualTo(160);
    }
}
