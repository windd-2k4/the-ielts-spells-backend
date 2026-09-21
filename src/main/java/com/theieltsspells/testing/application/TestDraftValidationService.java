package com.theieltsspells.testing.application;

import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.testing.application.dto.TestBankResponse;
import com.theieltsspells.testing.application.dto.TestValidationIssueResponse;
import com.theieltsspells.testing.application.dto.TestValidationResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-side publication gate for all four IELTS builders. The browser may
 * show more editorial hints, but these errors are authoritative and protect
 * published snapshots from incomplete authoring payloads.
 */
@Service
class TestDraftValidationService {

    TestValidationResponse validate(TestBankResponse test) {
        var issues = new ArrayList<TestValidationIssueResponse>();
        if (isBlank(test.title())) {
            error(issues, "test-title", "Thông tin chung", null,
                    "Tên đề thi không được để trống.", "test-builder-workspace");
        }
        if (test.durationMinutes() <= 0) {
            error(issues, "test-duration", "Thông tin chung", null,
                    "Thời lượng làm bài phải lớn hơn 0 phút.", "test-builder-workspace");
        }

        switch (test.skill()) {
            case READING -> validateReading(test, issues);
            case LISTENING -> validateListening(test, issues);
            case WRITING -> validateWriting(test, issues);
            case SPEAKING -> validateSpeaking(test, issues);
            default -> error(issues, "test-skill", "Thông tin chung", null,
                    "Đề thi phải thuộc một trong bốn kỹ năng IELTS.", "test-builder-workspace");
        }
        boolean publishable = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
        return new TestValidationResponse(test.id(), test.draftRevision(), publishable, List.copyOf(issues));
    }

    private void validateReading(TestBankResponse test, List<TestValidationIssueResponse> issues) {
        ReadingDraftValidator.validate(test, issues);
    }

    private void validateListening(TestBankResponse test, List<TestValidationIssueResponse> issues) {
        var parts = maps(test.builderContent().get("parts"));
        if ("FULL_TEST".equals(test.testType()) && parts.size() != 4) {
            error(issues, "listening-parts", "Cấu trúc Listening", null,
                    "Full Listening cần có đúng 4 Part.", "listening-question-panel");
        }
        if (parts.isEmpty()) {
            error(issues, "listening-empty", "Cấu trúc Listening", null,
                    "Đề Listening chưa có Part nào.", "listening-question-panel");
        }
        for (int index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            int partNo = number(part.get("partNo"), index + 1);
            String title = "Listening Part " + partNo;
            if (isBlank(text(part.get("audioUrl")))) {
                error(issues, "listening-" + partNo + "-audio", title, null,
                        "Chưa tải audio cho Part này.", "listening-question-panel");
            }
            validateQuestionGroups(maps(part.get("questionGroups")), title, issues);
        }
    }

    private void validateWriting(TestBankResponse test, List<TestValidationIssueResponse> issues) {
        var content = test.builderContent();
        var tasks = maps(content.containsKey("tasks") ? content.get("tasks") : content.get("writingTasks"));
        if (tasks.isEmpty()) {
            error(issues, "writing-tasks", "Cấu trúc Writing", null,
                    "Đề chưa có Writing Task.", "test-builder-workspace");
            return;
        }
        if ("FULL_TEST".equals(test.testType()) && tasks.size() != 2) {
            error(issues, "writing-full-tasks", "Cấu trúc Writing", null,
                    "Full Writing cần có đúng Task 1 và Task 2.", "test-builder-workspace");
        }
        for (int index = 0; index < tasks.size(); index++) {
            var task = tasks.get(index);
            int taskNo = number(task.get("taskNo"), index + 1);
            String title = "Writing Task " + taskNo;
            if (isBlank(stripHtml(text(task.get("promptHtml"))))) {
                error(issues, "writing-" + taskNo + "-prompt", title, null,
                        "Chưa nhập đề bài.", "test-builder-workspace");
            }
            if (number(task.get("minWords"), 0) <= 0) {
                error(issues, "writing-" + taskNo + "-min-words", title, null,
                        "Số từ tối thiểu phải lớn hơn 0.", "test-builder-workspace");
            }
            if (number(task.get("suggestedTimeMinutes"), 0) <= 0) {
                error(issues, "writing-" + taskNo + "-time", title, null,
                        "Thời gian gợi ý phải lớn hơn 0.", "test-builder-workspace");
            }
        }
    }

    private void validateSpeaking(TestBankResponse test, List<TestValidationIssueResponse> issues) {
        var parts = maps(test.builderContent().get("parts"));
        if (parts.isEmpty()) {
            error(issues, "speaking-parts", "Cấu trúc Speaking", null,
                    "Đề chưa có Speaking Part.", "test-builder-workspace");
            return;
        }
        if ("FULL_TEST".equals(test.testType()) && parts.size() != 3) {
            error(issues, "speaking-full-parts", "Cấu trúc Speaking", null,
                    "Full Speaking cần có đúng Part 1, 2 và 3.", "test-builder-workspace");
        }
        for (int index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            int partNo = number(part.get("partNo"), index + 1);
            String title = "Speaking Part " + partNo;
            if (isBlank(text(part.get("topicTitle")))) {
                warning(issues, "speaking-" + partNo + "-topic", title, null,
                        "Chưa nhập tên chủ đề.", "test-builder-workspace");
            }
            if (partNo == 2) {
                if (isBlank(stripHtml(text(part.get("cueCardPromptHtml"))))) {
                    error(issues, "speaking-2-cue-card", title, null,
                            "Chưa nhập đề bài cue card.", "test-builder-workspace");
                }
                continue;
            }
            var questions = maps(part.get("questions"));
            if (questions.isEmpty()) {
                error(issues, "speaking-" + partNo + "-questions", title, null,
                        "Part này cần có ít nhất một câu hỏi.", "test-builder-workspace");
                continue;
            }
            for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
                var question = questions.get(questionIndex);
                if (isBlank(text(question.get("promptText")))) {
                    error(issues, "speaking-" + partNo + "-question-" + (questionIndex + 1), title, questionIndex + 1,
                            "Chưa nhập nội dung câu hỏi.", text(question.get("id"), "test-builder-workspace"));
                }
            }
        }
    }

    private void validateQuestionGroups(List<Map<String, Object>> groups, String sectionTitle,
                                        List<TestValidationIssueResponse> issues) {
        if (groups.isEmpty()) {
            error(issues, sectionTitle.toLowerCase().replace(" ", "-") + "-groups", sectionTitle, null,
                    "Part chưa có Question Group.", "listening-question-panel");
            return;
        }
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            var group = groups.get(groupIndex);
            String groupTitle = text(group.get("title"), sectionTitle + " · Question Group " + (groupIndex + 1));
            var questions = maps(group.get("questions"));
            if (isBlank(text(group.get("instructions")))) {
                error(issues, "group-" + groupIndex + "-instructions", groupTitle, null,
                        "Question Group chưa có hướng dẫn.", text(group.get("id"), "listening-question-panel"));
            }
            if (questions.isEmpty()) {
                error(issues, "group-" + groupIndex + "-questions", groupTitle, null,
                        "Question Group cần có ít nhất một câu hỏi.", text(group.get("id"), "listening-question-panel"));
            }
            for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
                var question = questions.get(questionIndex);
                int no = number(question.get("number"), questionIndex + 1);
                String targetId = text(question.get("id"), "listening-question-panel");
                if (isBlank(text(question.get("prompt")))) {
                    error(issues, "question-" + no + "-prompt", groupTitle, no,
                            "Chưa nhập nội dung câu hỏi.", targetId);
                }
                if (list(question.get("correctAnswers")).isEmpty()) {
                    error(issues, "question-" + no + "-answer", groupTitle, no,
                            "Chưa nhập đáp án đúng.", targetId);
                }
            }
        }
    }

    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> items)) return List.of();
        var result = new ArrayList<Map<String, Object>>();
        for (var item : items) {
            if (item instanceof Map<?, ?> raw) {
                var map = new java.util.LinkedHashMap<String, Object>();
                raw.forEach((key, entry) -> map.put(String.valueOf(key), entry));
                result.add(map);
            }
        }
        return result;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String text(Object value) {
        return text(value, "");
    }

    private static String text(Object value, String fallback) {
        if (value == null) return fallback;
        String valueAsText = value.toString().trim();
        return valueAsText.isEmpty() ? fallback : valueAsText;
    }

    private static String stripHtml(String value) {
        return value.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void error(List<TestValidationIssueResponse> issues, String id, String sectionTitle,
                              Integer questionNo, String message, String targetId) {
        issues.add(new TestValidationIssueResponse(id, "ERROR", sectionTitle, questionNo, message, targetId));
    }

    private static void warning(List<TestValidationIssueResponse> issues, String id, String sectionTitle,
                                Integer questionNo, String message, String targetId) {
        issues.add(new TestValidationIssueResponse(id, "WARNING", sectionTitle, questionNo, message, targetId));
    }
}
