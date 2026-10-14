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
}
