package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.util.HtmlSanitizer;
import com.theieltsspells.testing.application.dto.TestBankResponse;
import com.theieltsspells.testing.application.dto.TestValidationIssueResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReadingDraftValidator {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "MULTIPLE_CHOICE",
            "MULTIPLE_ANSWERS",
            "FILL_IN_BLANK",
            "SHORT_ANSWER",
            "TRUE_FALSE_NOT_GIVEN",
            "YES_NO_NOT_GIVEN",
            "MATCHING_HEADINGS",
            "MATCHING_INFORMATION",
            "MATCHING_FEATURES",
            "MATCHING_SENTENCE_ENDINGS",
            "SENTENCE_COMPLETION",
            "SUMMARY_COMPLETION",
            "NOTE_COMPLETION",
            "TABLE_COMPLETION",
            "FLOW_CHART_COMPLETION",
            "DIAGRAM_LABELING"
    );

    private static final Set<String> SHARED_OPTION_TYPES = Set.of(
            "MATCHING_HEADINGS",
            "MATCHING_INFORMATION",
            "MATCHING_FEATURES",
            "MATCHING_SENTENCE_ENDINGS"
    );

    private static final Set<String> WORD_LIMIT_TYPES = Set.of(
            "FILL_IN_BLANK",
            "SHORT_ANSWER",
            "SENTENCE_COMPLETION",
            "SUMMARY_COMPLETION",
            "NOTE_COMPLETION",
            "TABLE_COMPLETION",
            "FLOW_CHART_COMPLETION",
            "DIAGRAM_LABELING"
    );

    private static final Set<String> TFNG_ANSWERS = Set.of("TRUE", "FALSE", "NOT_GIVEN");
    private static final Set<String> YNNG_ANSWERS = Set.of("YES", "NO", "NOT_GIVEN");

    private ReadingDraftValidator() {
    }

    static void validate(Map<String, Object> content) {
        validate(content, false);
    }

    static void validate(Map<String, Object> content, boolean fullReadingTest) {
        var issues = new ArrayList<TestValidationIssueResponse>();
        StrictReadingDraftValidator.validate(content, fullReadingTest ? "FULL_TEST" : "SINGLE_SKILL", 60, issues);
        var firstError = issues.stream()
                .filter(issue -> "ERROR".equals(issue.severity()))
                .findFirst();
        if (firstError.isPresent()) {
            throw new BusinessRuleException(firstError.get().message());
        }
    }

    static void validate(TestBankResponse test, List<TestValidationIssueResponse> issues) {
        StrictReadingDraftValidator.validate(test.builderContent(), test.testType(), test.durationMinutes(), issues);
    }

    private static void validateReadingContent(Map<String, Object> content, String testType, int durationMinutes,
                                               List<TestValidationIssueResponse> issues) {
        boolean fullTest = "FULL_TEST".equalsIgnoreCase(testType);
        var passages = passages(content);
        var seenIds = new HashSet<String>();

        if (fullTest) {
            if (passages.size() != 3) {
                error(issues, "reading-full-passages", "Cấu trúc đề", null,
                        "Full Reading phải có đúng 3 passages trước khi xuất bản", "reading-passage-navigation");
            }
            if (durationMinutes != 60) {
                error(issues, "duration", "Thông tin chung", null,
                        "Full Reading phải có thời lượng 60 phút.", "test-builder-header");
            }
        } else {
            if (passages.isEmpty()) {
                error(issues, "reading-empty-passages", "Cấu trúc đề", null,
                        "Đề Reading phải có ít nhất một passage trước khi xuất bản", "reading-question-panel");
            }
        }

        var allQuestions = new ArrayList<QuestionContext>();

        for (int pIdx = 0; pIdx < passages.size(); pIdx++) {
            var passage = passages.get(pIdx);
            int passageNo = number(passage.get("passageNo"), pIdx + 1);
            String passageId = text(passage.get("id"));
            String sectionTitle = "Passage " + passageNo;

            validateUniqueId(passageId, "passage-" + passageNo, "Passage", issues, seenIds);

            String cleanContent = HtmlSanitizer.stripToPlainText(text(passage.get("content")));
            if (cleanContent.isBlank()) {
                error(issues, "passage-" + (passageId.isBlank() ? pIdx + 1 : passageId) + "-content",
                        sectionTitle, null, "Passage " + passageNo + " chưa có nội dung bài đọc.",
                        passageId.isBlank() ? "passage-tab-" + (pIdx + 1) : passageId);
            }

            var groups = maps(passage.get("questionGroups"));
            if (groups.isEmpty()) {
                error(issues, "passage-" + (passageId.isBlank() ? pIdx + 1 : passageId) + "-groups",
                        sectionTitle, null, "Passage " + passageNo + " chưa có Question Group.",
                        passageId.isBlank() ? "passage-tab-" + (pIdx + 1) : passageId);
                continue;
            }

            for (int gIdx = 0; gIdx < groups.size(); gIdx++) {
                var group = groups.get(gIdx);
                String groupId = text(group.get("id"));
                String groupTitle = text(group.get("title"));
                String groupTargetId = groupId.isBlank() ? "reading-question-panel" : groupId;

                validateUniqueId(groupId, "group-" + pIdx + "-" + gIdx, "Question Group", issues, seenIds);

                if (groupTitle.isBlank()) {
                    error(issues, "group-" + (groupId.isBlank() ? gIdx : groupId) + "-title",
                            sectionTitle, null, "Question Group chưa có tiêu đề", groupTargetId);
                }

                String type = text(group.get("typeFormat"));
                if (!SUPPORTED_TYPES.contains(type)) {
                    error(issues, "group-" + (groupId.isBlank() ? gIdx : groupId) + "-type",
                            groupTitle.isBlank() ? sectionTitle : groupTitle, null,
                            "Question Group '" + groupTitle + "' có dạng bài không được hỗ trợ", groupTargetId);
                }

                if (text(group.get("instructions")).isBlank()) {
                    error(issues, "group-" + (groupId.isBlank() ? gIdx : groupId) + "-instructions",
                            groupTitle.isBlank() ? sectionTitle : groupTitle, null,
                            "Question Group '" + groupTitle + "' chưa có hướng dẫn làm bài", groupTargetId);
                }

                var questions = maps(group.get("questions"));
                if (questions.isEmpty()) {
                    error(issues, "group-" + (groupId.isBlank() ? gIdx : groupId) + "-questions",
                            groupTitle.isBlank() ? sectionTitle : groupTitle, null,
                            "Question Group '" + groupTitle + "' phải có ít nhất một câu hỏi", groupTargetId);
                    continue;
                }

                String answerSource = text(group.get("answerSource"));
                boolean optionBankRequired = SHARED_OPTION_TYPES.contains(type)
                        || ("SUMMARY_COMPLETION".equals(type) && "OPTION_BANK".equals(answerSource));
                var sharedOptions = maps(group.get("sharedOptions"));
                if (optionBankRequired) {
                    if (!hasUsableOption(sharedOptions)) {
                        error(issues, "group-" + (groupId.isBlank() ? gIdx : groupId) + "-options",
                                groupTitle.isBlank() ? sectionTitle : groupTitle, null,
                                "Question Group '" + groupTitle + "' chưa có Option bank hợp lệ", groupTargetId);
                    }
                    boolean allowReused = booleanValue(group.get("allowOptionReused"));
                    if (!allowReused && sharedOptions.size() < questions.size()) {
                        error(issues, "group-" + (groupId.isBlank() ? gIdx : groupId) + "-options-count",
                                groupTitle.isBlank() ? sectionTitle : groupTitle, null,
                                "Option bank của Question Group '" + groupTitle + "' không đủ số lượng lựa chọn cho từng câu hỏi khi không cho phép dùng lại", groupTargetId);
                    }
                }

                for (var opt : sharedOptions) {
                    validateUniqueId(text(opt.get("id")), "shared-option", "Lựa chọn", issues, seenIds);
                }

                boolean passageAnswer = !"OPTION_BANK".equals(answerSource);
                if (WORD_LIMIT_TYPES.contains(type) && passageAnswer && text(group.get("wordLimitRule")).isBlank()) {
                    error(issues, "group-" + (groupId.isBlank() ? gIdx : groupId) + "-word-limit",
                            groupTitle.isBlank() ? sectionTitle : groupTitle, null,
                            "Question Group '" + groupTitle + "' chưa cấu hình giới hạn từ", groupTargetId);
                }

                String gapFillTemplate = text(group.get("gapFillTemplate"));
                if (!gapFillTemplate.isBlank()) {
                    validateGapFillTemplate(gapFillTemplate, questions.size(), groupTitle.isBlank() ? sectionTitle : groupTitle, groupTargetId, issues);
                }

                int requiredAnswerCount = number(group.get("requiredAnswerCount"), 2);
                if ("MULTIPLE_ANSWERS".equals(type) && (requiredAnswerCount < 2 || requiredAnswerCount > 6)) {
                    error(issues, "group-" + (groupId.isBlank() ? gIdx : groupId) + "-required-count",
                            groupTitle.isBlank() ? sectionTitle : groupTitle, null,
                            "Question Group '" + groupTitle + "' có số đáp án cần chọn không hợp lệ (phải từ 2 đến 6)", groupTargetId);
                }

                for (int qIdx = 0; qIdx < questions.size(); qIdx++) {
                    var question = questions.get(qIdx);
                    String questionId = text(question.get("id"));
                    int questionNo = number(question.get("number"), 0);
                    String qTargetId = questionId.isBlank() ? groupTargetId : questionId;

                    validateUniqueId(questionId, "question-" + questionNo, "Câu hỏi", issues, seenIds);
                    allQuestions.add(new QuestionContext(questionNo, questionId, groupTitle.isBlank() ? sectionTitle : groupTitle, qTargetId));

                    if (text(question.get("prompt")).isBlank()) {
                        error(issues, "question-" + (questionId.isBlank() ? questionNo : questionId) + "-prompt",
                                groupTitle.isBlank() ? sectionTitle : groupTitle, questionNo,
                                "Câu " + questionNo + " chưa có nội dung câu hỏi", qTargetId);
                    }

                    var correctAnswers = strings(question.get("correctAnswers"));
                    if (correctAnswers.isEmpty()) {
                        error(issues, "question-" + (questionId.isBlank() ? questionNo : questionId) + "-answer",
                                groupTitle.isBlank() ? sectionTitle : groupTitle, questionNo,
                                "Câu " + questionNo + " chưa có đáp án đúng", qTargetId);
                    }

                    if ("MULTIPLE_CHOICE".equals(type)) {
                        var options = maps(question.get("options"));
                        if (options.isEmpty()) {
                            error(issues, "question-" + (questionId.isBlank() ? questionNo : questionId) + "-mc-options",
                                    groupTitle.isBlank() ? sectionTitle : groupTitle, questionNo,
                                    "Câu " + questionNo + " chưa có các lựa chọn A, B, C, D", qTargetId);
                        }
                        for (var opt : options) {
                            validateUniqueId(text(opt.get("id")), "question-option", "Lựa chọn câu hỏi", issues, seenIds);
                        }
                    }

                    if ("MULTIPLE_ANSWERS".equals(type) && correctAnswers.size() != requiredAnswerCount) {
                        error(issues, "question-" + (questionId.isBlank() ? questionNo : questionId) + "-ans-count",
                                groupTitle.isBlank() ? sectionTitle : groupTitle, questionNo,
                                "Câu " + questionNo + " phải có đúng " + requiredAnswerCount + " đáp án đúng", qTargetId);
                    }

                    if ("TRUE_FALSE_NOT_GIVEN".equals(type)) {
                        for (String ans : correctAnswers) {
                            if (!TFNG_ANSWERS.contains(ans.trim().toUpperCase())) {
                                error(issues, "question-" + questionNo + "-tfng", groupTitle, questionNo,
                                        "Câu " + questionNo + " đáp án phải là TRUE, FALSE hoặc NOT_GIVEN", qTargetId);
                            }
                        }
                    }

                    if ("YES_NO_NOT_GIVEN".equals(type)) {
                        for (String ans : correctAnswers) {
                            if (!YNNG_ANSWERS.contains(ans.trim().toUpperCase())) {
                                error(issues, "question-" + questionNo + "-ynng", groupTitle, questionNo,
                                        "Câu " + questionNo + " đáp án phải là YES, NO hoặc NOT_GIVEN", qTargetId);
                            }
                        }
                    }
                }
            }
        }

        // Validate continuous numbering across all questions
        if (fullTest) {
            if (allQuestions.size() != 40) {
                error(issues, "reading-question-count", "Cấu trúc đề", null,
                        "Full Reading phải có đúng 40 câu hỏi (hiện tại: " + allQuestions.size() + ").", "reading-question-panel");
            }
            validateSequentialNumbering(allQuestions, 40, issues);
        } else {
            if (allQuestions.isEmpty()) {
                error(issues, "reading-no-questions", "Câu hỏi", null,
                        "Đề Reading cần có ít nhất một câu hỏi.", "reading-question-panel");
            } else {
                validateSequentialNumbering(allQuestions, allQuestions.size(), issues);
            }
        }
    }

    private static void validateSequentialNumbering(List<QuestionContext> questions, int expectedTotal,
                                                    List<TestValidationIssueResponse> issues) {
        boolean sequential = true;
        for (int i = 0; i < questions.size(); i++) {
            int expectedNo = i + 1;
            if (questions.get(i).number() != expectedNo) {
                sequential = false;
                break;
            }
        }
        if (!sequential) {
            error(issues, "reading-question-numbering", "Số thứ tự câu hỏi", null,
                    "Các câu hỏi phải được đánh số liên tục từ 1 đến " + expectedTotal + ".", "reading-question-panel");
        }
    }

    private static void validateUniqueId(String id, String prefix, String subject,
                                        List<TestValidationIssueResponse> issues, Set<String> seenIds) {
        if (id == null || id.isBlank()) {
            error(issues, prefix + "-blank-id", "Cấu trúc ID", null,
                    subject + " thiếu ID định danh.", "reading-question-panel");
        } else if (!seenIds.add(id.trim())) {
            error(issues, prefix + "-" + id + "-duplicate", "Cấu trúc ID", null,
                    subject + " có ID bị trùng lặp: " + id, "reading-question-panel");
        }
    }

    private static void validateGapFillTemplate(String template, int questionCount, String sectionTitle,
                                                String targetId, List<TestValidationIssueResponse> issues) {
        Pattern pattern = Pattern.compile("\\[\\[(\\d+)\\]\\]");
        Matcher matcher = pattern.matcher(template);
        Set<Integer> foundPlaceholders = new HashSet<>();
        while (matcher.find()) {
            try {
                foundPlaceholders.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (foundPlaceholders.size() != questionCount) {
            error(issues, targetId + "-gap-template", sectionTitle, null,
                    "Mẫu Gap filling phải chứa đúng " + questionCount + " vị trí [[n]] tương ứng với số câu hỏi.", targetId);
        }
    }

    private static List<Map<String, Object>> passages(Map<String, Object> content) {
        var passages = maps(content.get("passages"));
        if (!passages.isEmpty()) {
            return passages;
        }

        var rootGroups = maps(content.get("questionGroups"));
        if (rootGroups.isEmpty()) {
            return List.of();
        }
        var section = new java.util.LinkedHashMap<String, Object>();
        section.put("id", "legacy-passage-1");
        section.put("passageNo", 1);
        section.put("title", text(content.get("title"), "Reading Passage 1"));
        section.put("content", legacyPassageContent(content));
        section.put("questionGroups", rootGroups);
        return List.of(section);
    }

    private static String legacyPassageContent(Map<String, Object> content) {
        Object value = content.get("passageContent");
        if (value instanceof Map<?, ?> values) {
            return text(values.get("1"));
        }
        return text(value);
    }

    private static boolean hasUsableOption(Object value) {
        return maps(value).stream().anyMatch(option ->
                !text(option.get("code")).isBlank() && !text(option.get("text")).isBlank());
    }

    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        var result = new ArrayList<Map<String, Object>>();
        for (var item : values) {
            if (item instanceof Map<?, ?> map) {
                var normalized = new java.util.LinkedHashMap<String, Object>();
                map.forEach((k, v) -> normalized.put(String.valueOf(k), v));
                result.add(normalized);
            }
        }
        return result;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream()
                .filter(item -> item != null && !item.toString().isBlank())
                .map(item -> item.toString().trim())
                .toList();
    }

    private static String text(Object value) {
        return text(value, "");
    }

    private static String text(Object value, String fallback) {
        if (value == null) return fallback;
        String val = value.toString().trim();
        return val.isEmpty() ? fallback : val;
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean b) return b;
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static void error(List<TestValidationIssueResponse> issues, String id, String sectionTitle,
                               Integer questionNo, String message, String targetId) {
        issues.add(new TestValidationIssueResponse(id, "ERROR", sectionTitle, questionNo, message, targetId));
    }

    private record QuestionContext(int number, String id, String sectionTitle, String targetId) {
    }
}
