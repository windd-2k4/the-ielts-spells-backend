package com.theieltsspells.testing.application;

import com.theieltsspells.shared.util.HtmlSanitizer;
import com.theieltsspells.testing.application.dto.TestValidationIssueResponse;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Authoritative validation for Reading drafts before review or publication. */
final class StrictReadingDraftValidator {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "MULTIPLE_CHOICE", "MULTIPLE_ANSWERS", "FILL_IN_BLANK", "SHORT_ANSWER",
            "TRUE_FALSE_NOT_GIVEN", "YES_NO_NOT_GIVEN", "MATCHING_HEADINGS",
            "MATCHING_INFORMATION", "MATCHING_FEATURES", "MATCHING_SENTENCE_ENDINGS",
            "SENTENCE_COMPLETION", "SUMMARY_COMPLETION", "NOTE_COMPLETION",
            "TABLE_COMPLETION", "FLOW_CHART_COMPLETION", "DIAGRAM_LABELING"
    );
    private static final Set<String> SHARED_OPTION_TYPES = Set.of(
            "MATCHING_HEADINGS", "MATCHING_INFORMATION", "MATCHING_FEATURES",
            "MATCHING_SENTENCE_ENDINGS"
    );
    private static final Set<String> WORD_LIMIT_TYPES = Set.of(
            "FILL_IN_BLANK", "SHORT_ANSWER", "SENTENCE_COMPLETION", "SUMMARY_COMPLETION",
            "NOTE_COMPLETION", "TABLE_COMPLETION", "FLOW_CHART_COMPLETION", "DIAGRAM_LABELING"
    );
    private static final Set<String> GAP_TEMPLATE_TYPES = Set.of(
            "FILL_IN_BLANK", "SENTENCE_COMPLETION", "SUMMARY_COMPLETION", "NOTE_COMPLETION",
            "TABLE_COMPLETION", "FLOW_CHART_COMPLETION", "DIAGRAM_LABELING"
    );
    private static final Set<String> TFNG_ANSWERS = Set.of("TRUE", "FALSE", "NOT_GIVEN");
    private static final Set<String> YNNG_ANSWERS = Set.of("YES", "NO", "NOT_GIVEN");
    private static final Set<String> EVIDENCE_MODES = Set.of(
            "DIRECT_QUOTE", "WHOLE_PARAGRAPH", "NO_DIRECT_EVIDENCE"
    );
    private static final Set<String> SOLUTION_VISIBILITIES = Set.of(
            "TEACHER_ONLY", "STUDENT_AFTER_ASSIGN", "STUDENT_AFTER_SUBMIT"
    );
    private static final Pattern GAP_PLACEHOLDER = Pattern.compile("\\[\\[(\\d+)]]");

    private StrictReadingDraftValidator() {
    }

    static void validate(Map<String, Object> content, String testType, int durationMinutes,
                         List<TestValidationIssueResponse> issues) {
        boolean fullTest = "FULL_TEST".equalsIgnoreCase(testType);
        var passages = passages(content == null ? Map.of() : content);
        var seenIds = new HashSet<String>();
        var allQuestions = new ArrayList<Integer>();

        if (fullTest && passages.size() != 3) {
            error(issues, "reading-full-passages", "Cấu trúc đề", null,
                    "Full Reading phải có đúng 3 passages trước khi xuất bản", "reading-passage-navigation");
        }
        if (fullTest && durationMinutes != 60) {
            error(issues, "duration", "Thông tin chung", null,
                    "Full Reading phải có thời lượng 60 phút.", "test-builder-header");
        }
        if (!fullTest && passages.isEmpty()) {
            error(issues, "reading-empty-passages", "Cấu trúc đề", null,
                    "Đề Reading phải có ít nhất một passage trước khi xuất bản", "reading-question-panel");
        }

        for (int passageIndex = 0; passageIndex < passages.size(); passageIndex++) {
            validatePassage(passages.get(passageIndex), passageIndex, issues, seenIds, allQuestions);
        }

        int expectedQuestionCount = fullTest ? 40 : allQuestions.size();
        if (fullTest && allQuestions.size() != 40) {
            error(issues, "reading-question-count", "Cấu trúc đề", null,
                    "Full Reading phải có đúng 40 câu hỏi (hiện tại: " + allQuestions.size() + ").",
                    "reading-question-panel");
        }
        if (!fullTest && allQuestions.isEmpty()) {
            error(issues, "reading-no-questions", "Câu hỏi", null,
                    "Đề Reading cần có ít nhất một câu hỏi.", "reading-question-panel");
        }
        for (int index = 0; index < allQuestions.size(); index++) {
            if (allQuestions.get(index) != index + 1) {
                error(issues, "reading-question-numbering", "Số thứ tự câu hỏi", null,
                        "Các câu hỏi phải được đánh số liên tục từ 1 đến " + expectedQuestionCount + ".",
                        "reading-question-panel");
                break;
            }
        }
    }

    private static void validatePassage(Map<String, Object> passage, int passageIndex,
                                        List<TestValidationIssueResponse> issues, Set<String> seenIds,
                                        List<Integer> allQuestions) {
        int expectedNo = passageIndex + 1;
        String passageId = text(passage.get("id"));
        String targetId = passageId.isBlank() ? "passage-tab-" + expectedNo : passageId;
        String sectionTitle = "Passage " + expectedNo;

        validateUniqueId(passageId, "passage-" + expectedNo, "Passage", targetId, issues, seenIds);
        if (number(passage.get("passageNo"), 0) != expectedNo) {
            error(issues, "passage-" + expectedNo + "-number", sectionTitle, null,
                    "Passage phải được đánh số liên tục từ 1 theo đúng thứ tự.", targetId);
        }
        if (text(passage.get("title")).isBlank()) {
            error(issues, "passage-" + expectedNo + "-title", sectionTitle, null,
                    sectionTitle + " chưa có tiêu đề.", targetId);
        }
        if (plainText(passage.get("content")).isBlank()) {
            error(issues, "passage-" + expectedNo + "-content", sectionTitle, null,
                    sectionTitle + " chưa có nội dung bài đọc.", targetId);
        }

        var groups = maps(passage.get("questionGroups"));
        if (groups.isEmpty()) {
            error(issues, "passage-" + expectedNo + "-groups", sectionTitle, null,
                    sectionTitle + " chưa có Question Group.", targetId);
            return;
        }
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            validateGroup(groups.get(groupIndex), passageIndex, groupIndex, sectionTitle,
                    issues, seenIds, allQuestions);
        }
    }

    private static void validateGroup(Map<String, Object> group, int passageIndex, int groupIndex,
                                      String passageTitle, List<TestValidationIssueResponse> issues,
                                      Set<String> seenIds, List<Integer> allQuestions) {
        String groupId = text(group.get("id"));
        String groupTitle = text(group.get("title"));
        String sectionTitle = groupTitle.isBlank() ? passageTitle : groupTitle;
        String targetId = groupId.isBlank() ? "reading-question-panel" : groupId;
        String type = normalized(group.get("typeFormat"));

        validateUniqueId(groupId, "group-" + passageIndex + "-" + groupIndex,
                "Question Group", targetId, issues, seenIds);
        if (groupTitle.isBlank()) {
            error(issues, "group-" + groupIndex + "-title", passageTitle, null,
                    "Question Group chưa có tiêu đề", targetId);
        }
        if (!SUPPORTED_TYPES.contains(type)) {
            error(issues, "group-" + groupIndex + "-type", sectionTitle, null,
                    "Question Group có dạng bài không được hỗ trợ", targetId);
        }
        if (plainText(group.get("instructions")).isBlank()) {
            error(issues, "group-" + groupIndex + "-instructions", sectionTitle, null,
                    "Question Group chưa có hướng dẫn làm bài", targetId);
        }

        var questions = maps(group.get("questions"));
        if (questions.isEmpty()) {
            error(issues, "group-" + groupIndex + "-questions", sectionTitle, null,
                    "Question Group phải có ít nhất một câu hỏi", targetId);
            return;
        }

        String answerSource = normalized(group.get("answerSource"));
        if (!answerSource.isBlank() && !Set.of("PASSAGE", "OPTION_BANK").contains(answerSource)) {
            error(issues, "group-" + groupIndex + "-answer-source", sectionTitle, null,
                    "Nguồn đáp án phải là PASSAGE hoặc OPTION_BANK.", targetId);
        }
        boolean optionBankRequired = SHARED_OPTION_TYPES.contains(type)
                || ("SUMMARY_COMPLETION".equals(type) && "OPTION_BANK".equals(answerSource));
        boolean passageAnswer = !optionBankRequired && !"OPTION_BANK".equals(answerSource);
        var sharedOptionIds = validateOptions(maps(group.get("sharedOptions")), optionBankRequired,
                "Option bank", sectionTitle, null, targetId, issues, seenIds);
        if (optionBankRequired && sharedOptionIds.size() < 2) {
            error(issues, "group-" + groupIndex + "-options", sectionTitle, null,
                    "Option bank phải có ít nhất 2 lựa chọn hợp lệ.", targetId);
        }
        if (WORD_LIMIT_TYPES.contains(type) && passageAnswer && text(group.get("wordLimitRule")).isBlank()) {
            error(issues, "group-" + groupIndex + "-word-limit", sectionTitle, null,
                    "Question Group chưa cấu hình giới hạn từ", targetId);
        }

        int requiredAnswerCount = number(group.get("requiredAnswerCount"), 2);
        if ("MULTIPLE_ANSWERS".equals(type) && (requiredAnswerCount < 2 || requiredAnswerCount > 6)) {
            error(issues, "group-" + groupIndex + "-required-count", sectionTitle, null,
                    "Số đáp án cần chọn phải từ 2 đến 6.", targetId);
        }

        var questionNumbers = new LinkedHashSet<Integer>();
        var usedSharedAnswers = new HashSet<String>();
        boolean allowOptionReused = booleanValue(group.get("allowOptionReused"));
        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
            int questionNo = validateQuestion(questions.get(questionIndex), questionIndex, type,
                    requiredAnswerCount, sharedOptionIds, optionBankRequired, allowOptionReused,
                    usedSharedAnswers, sectionTitle, targetId, issues, seenIds, allQuestions);
            questionNumbers.add(questionNo);
        }

        if (GAP_TEMPLATE_TYPES.contains(type) && passageAnswer) {
            String template = text(group.get("gapFillTemplate"));
            if (template.isBlank()) {
                error(issues, "group-" + groupIndex + "-gap-template-missing", sectionTitle, null,
                        "Dạng bài này cần mẫu Gap filling chứa các vị trí [[n]].", targetId);
            } else {
                validateGapFillTemplate(template, questionNumbers, sectionTitle, targetId, issues);
            }
        }
    }

    private static int validateQuestion(Map<String, Object> question, int questionIndex, String groupType,
                                        int requiredAnswerCount, Set<String> sharedOptionIds,
                                        boolean optionBankRequired, boolean allowOptionReused,
                                        Set<String> usedSharedAnswers, String sectionTitle, String groupTargetId,
                                        List<TestValidationIssueResponse> issues, Set<String> seenIds,
                                        List<Integer> allQuestions) {
        String questionId = text(question.get("id"));
        int questionNo = number(question.get("number"), 0);
        String targetId = questionId.isBlank() ? groupTargetId : questionId;
        String prefix = "question-" + (questionId.isBlank() ? questionIndex : questionId);
        String questionType = normalized(question.get("typeFormat"));

        validateUniqueId(questionId, prefix, "Câu hỏi", targetId, issues, seenIds);
        allQuestions.add(questionNo);
        if (questionNo < 1) {
            error(issues, prefix + "-number", sectionTitle, questionNo,
                    "Câu hỏi phải có số thứ tự lớn hơn 0.", targetId);
        }
        if (!questionType.isBlank() && !questionType.equals(groupType)) {
            error(issues, prefix + "-type", sectionTitle, questionNo,
                    "Loại câu hỏi phải trùng với loại của Question Group.", targetId);
        }
        if (plainText(question.get("prompt")).isBlank()) {
            error(issues, prefix + "-prompt", sectionTitle, questionNo,
                    "Câu " + questionNo + " chưa có nội dung câu hỏi", targetId);
        }
        if (!positiveDecimal(question.get("maxScore"))) {
            error(issues, prefix + "-score", sectionTitle, questionNo,
                    "Điểm tối đa của câu " + questionNo + " phải lớn hơn 0.", targetId);
        }

        var correctAnswers = strings(question.get("correctAnswers"));
        var acceptableAnswers = strings(question.get("acceptableAnswers"));
        validateAnswerValues(question.get("correctAnswers"), correctAnswers, true,
                prefix, sectionTitle, questionNo, targetId, issues);
        validateAnswerValues(question.get("acceptableAnswers"), acceptableAnswers, false,
                prefix + "-acceptable", sectionTitle, questionNo, targetId, issues);

        Set<String> questionOptionIds = Set.of();
        if ("MULTIPLE_CHOICE".equals(groupType) || "MULTIPLE_ANSWERS".equals(groupType)) {
            questionOptionIds = validateOptions(maps(question.get("options")), true, "Lựa chọn câu hỏi",
                    sectionTitle, questionNo, targetId, issues, seenIds);
            if (questionOptionIds.size() < 2) {
                error(issues, prefix + "-options", sectionTitle, questionNo,
                        "Câu " + questionNo + " phải có ít nhất 2 lựa chọn hợp lệ.", targetId);
            }
        }

        if ("MULTIPLE_CHOICE".equals(groupType)) {
            if (correctAnswers.size() != 1) {
                error(issues, prefix + "-answer-count", sectionTitle, questionNo,
                        "Câu " + questionNo + " phải có đúng một đáp án đúng.", targetId);
            }
            validateReferences(correctAnswers, questionOptionIds, prefix, sectionTitle, questionNo, targetId, issues);
        } else if ("MULTIPLE_ANSWERS".equals(groupType)) {
            if (correctAnswers.size() != requiredAnswerCount) {
                error(issues, prefix + "-answer-count", sectionTitle, questionNo,
                        "Câu " + questionNo + " phải có đúng " + requiredAnswerCount + " đáp án đúng.", targetId);
            }
            validateReferences(correctAnswers, questionOptionIds, prefix, sectionTitle, questionNo, targetId, issues);
        } else if (optionBankRequired) {
            validateReferences(correctAnswers, sharedOptionIds, prefix, sectionTitle, questionNo, targetId, issues);
            if (!allowOptionReused) {
                for (String answer : correctAnswers) {
                    if (!usedSharedAnswers.add(answer)) {
                        error(issues, prefix + "-option-reused", sectionTitle, questionNo,
                                "Lựa chọn '" + answer + "' đã được dùng cho câu khác nhưng nhóm không cho phép dùng lại.",
                                targetId);
                    }
                }
            }
        }

        if ("TRUE_FALSE_NOT_GIVEN".equals(groupType)) {
            validateEnumAnswer(correctAnswers, TFNG_ANSWERS, "TRUE, FALSE hoặc NOT_GIVEN",
                    prefix, sectionTitle, questionNo, targetId, issues);
        } else if ("YES_NO_NOT_GIVEN".equals(groupType)) {
            validateEnumAnswer(correctAnswers, YNNG_ANSWERS, "YES, NO hoặc NOT_GIVEN",
                    prefix, sectionTitle, questionNo, targetId, issues);
        }
        validateSolutionAndEvidence(question, prefix, sectionTitle, questionNo, targetId, issues);
        return questionNo;
    }

    private static void validateSolutionAndEvidence(Map<String, Object> question, String prefix,
                                                    String sectionTitle, int questionNo, String targetId,
                                                    List<TestValidationIssueResponse> issues) {
        var nestedSolution = map(question.get("solution"));
        String explanation = plainText(solutionValue(nestedSolution, question, "explanation"));
        String visibility = normalized(solutionValue(nestedSolution, question, "solutionVisibility"));
        if (!visibility.isBlank() && !SOLUTION_VISIBILITIES.contains(visibility)) {
            error(issues, prefix + "-solution-visibility", sectionTitle, questionNo,
                    "Quyền hiển thị lời giải không hợp lệ.", targetId);
        }

        Object rawReasoningSteps = solutionValue(nestedSolution, question, "reasoningSteps");
        if (rawReasoningSteps != null && !(rawReasoningSteps instanceof List<?>)) {
            error(issues, prefix + "-reasoning-steps", sectionTitle, questionNo,
                    "Các bước suy luận phải là một danh sách.", targetId);
        }

        String relatedLessonUrl = text(solutionValue(nestedSolution, question, "relatedLessonUrl"));
        if (!relatedLessonUrl.isBlank() && !isHttpUrl(relatedLessonUrl)) {
            error(issues, prefix + "-related-lesson", sectionTitle, questionNo,
                    "Liên kết bài học liên quan phải là URL http(s) hợp lệ.", targetId);
        }

        if (question.containsKey("evidenceSpans")) {
            if (!(question.get("evidenceSpans") instanceof List<?>)) {
                error(issues, prefix + "-evidence-list", sectionTitle, questionNo,
                        "Danh sách bằng chứng phải có dạng danh sách.", targetId);
                return;
            }
            var spans = (List<?>) question.get("evidenceSpans");
            for (int index = 0; index < spans.size(); index++) {
                if (!(spans.get(index) instanceof Map<?, ?>)) {
                    error(issues, prefix + "-evidence-" + index, sectionTitle, questionNo,
                            "Mỗi bằng chứng phải có cấu trúc hợp lệ.", targetId);
                    continue;
                }
                validateEvidenceSpan(map(spans.get(index)), index, explanation, prefix, sectionTitle,
                        questionNo, targetId, issues);
            }
            return;
        }

        var legacy = map(question.get("passageSpan"));
        if (!legacy.isEmpty()) {
            validateEvidenceSpan(legacy, 0, explanation, prefix, sectionTitle,
                    questionNo, targetId, issues);
        }
    }

    private static void validateEvidenceSpan(Map<String, Object> span, int index, String explanation,
                                             String prefix, String sectionTitle, int questionNo,
                                             String targetId,
                                             List<TestValidationIssueResponse> issues) {
        String issuePrefix = prefix + "-evidence-" + index;
        String mode = normalized(span.get("mode"));
        if (mode.isBlank()) {
            mode = "DIRECT_QUOTE";
        }
        if (!EVIDENCE_MODES.contains(mode)) {
            error(issues, issuePrefix + "-mode", sectionTitle, questionNo,
                    "Loại bằng chứng không hợp lệ.", targetId);
            return;
        }
        Integer start = nullableInteger(span.get("start"));
        Integer end = nullableInteger(span.get("end"));
        if ("NO_DIRECT_EVIDENCE".equals(mode)) {
            if (start != null || end != null) {
                error(issues, issuePrefix + "-range", sectionTitle, questionNo,
                        "Bằng chứng 'không có thông tin trực tiếp' không được có vị trí trong Passage.", targetId);
            }
            if (explanation.isBlank()) {
                error(issues, issuePrefix + "-explanation", sectionTitle, questionNo,
                        "Bằng chứng 'không có thông tin trực tiếp' cần có lời giải cho học viên.", targetId);
            }
            return;
        }
        if (start == null || end == null || start < 0 || end <= start) {
            error(issues, issuePrefix + "-range", sectionTitle, questionNo,
                    "Bằng chứng phải có vị trí bắt đầu và kết thúc hợp lệ.", targetId);
            return;
        }
    }

    private static Set<String> validateOptions(List<Map<String, Object>> options, boolean required,
                                               String subject, String sectionTitle, Integer questionNo,
                                               String targetId, List<TestValidationIssueResponse> issues,
                                               Set<String> seenIds) {
        if (required && options.isEmpty()) {
            error(issues, targetId + "-options-empty", sectionTitle, questionNo,
                    subject + " chưa có lựa chọn.", targetId);
            return Set.of();
        }
        var optionIds = new LinkedHashSet<String>();
        var optionCodes = new HashSet<String>();
        for (int index = 0; index < options.size(); index++) {
            var option = options.get(index);
            String optionId = text(option.get("id"));
            String code = text(option.get("code"), text(option.get("label")));
            validateUniqueId(optionId, targetId + "-option-" + index, subject,
                    targetId, issues, seenIds);
            if (!optionId.isBlank()) {
                optionIds.add(optionId);
            }
            if (code.isBlank()) {
                error(issues, targetId + "-option-" + index + "-code", sectionTitle, questionNo,
                        subject + " thiếu mã/nhãn.", targetId);
            } else if (!optionCodes.add(code.toUpperCase(Locale.ROOT))) {
                error(issues, targetId + "-option-" + index + "-duplicate-code", sectionTitle, questionNo,
                        subject + " có mã/nhãn bị trùng: " + code, targetId);
            }
            if (plainText(option.get("text")).isBlank()) {
                error(issues, targetId + "-option-" + index + "-text", sectionTitle, questionNo,
                        subject + " thiếu nội dung.", targetId);
            }
        }
        return optionIds;
    }

    private static void validateAnswerValues(Object rawValue, List<String> answers, boolean required,
                                             String prefix, String sectionTitle, int questionNo,
                                             String targetId, List<TestValidationIssueResponse> issues) {
        if (required && answers.isEmpty()) {
            error(issues, prefix + "-answer", sectionTitle, questionNo,
                    "Câu " + questionNo + " chưa có đáp án đúng", targetId);
        }
        if (containsBlank(rawValue)) {
            error(issues, prefix + "-blank-answer", sectionTitle, questionNo,
                    "Danh sách đáp án của câu " + questionNo + " chứa giá trị trống.", targetId);
        }
        var normalizedAnswers = new HashSet<String>();
        for (String answer : answers) {
            if (!normalizedAnswers.add(normalizeAnswer(answer))) {
                error(issues, prefix + "-duplicate-answer", sectionTitle, questionNo,
                        "Danh sách đáp án của câu " + questionNo + " chứa giá trị trùng lặp.", targetId);
                break;
            }
        }
    }

    private static void validateReferences(List<String> answers, Set<String> allowedIds, String prefix,
                                           String sectionTitle, int questionNo, String targetId,
                                           List<TestValidationIssueResponse> issues) {
        for (String answer : answers) {
            if (!allowedIds.contains(answer)) {
                error(issues, prefix + "-answer-reference-" + answer, sectionTitle, questionNo,
                        "Đáp án '" + answer + "' của câu " + questionNo + " không tham chiếu lựa chọn hợp lệ.",
                        targetId);
            }
        }
    }

    private static void validateEnumAnswer(List<String> answers, Set<String> allowed, String description,
                                           String prefix, String sectionTitle, int questionNo,
                                           String targetId, List<TestValidationIssueResponse> issues) {
        if (answers.size() != 1 || !allowed.contains(normalized(answers.isEmpty() ? null : answers.getFirst()))) {
            error(issues, prefix + "-enum-answer", sectionTitle, questionNo,
                    "Câu " + questionNo + " phải có đúng một đáp án: " + description + ".", targetId);
        }
    }

    private static void validateGapFillTemplate(String template, Set<Integer> questionNumbers,
                                                String sectionTitle, String targetId,
                                                List<TestValidationIssueResponse> issues) {
        var matcher = GAP_PLACEHOLDER.matcher(template);
        var placeholders = new ArrayList<Integer>();
        while (matcher.find()) {
            placeholders.add(Integer.parseInt(matcher.group(1)));
        }
        var unique = new LinkedHashSet<>(placeholders);
        if (unique.size() != placeholders.size() || !unique.equals(questionNumbers)) {
            error(issues, targetId + "-gap-template", sectionTitle, null,
                    "Mẫu Gap filling phải chứa đúng một vị trí [[n]] cho từng số câu hỏi của nhóm.", targetId);
        }
    }

    private static void validateUniqueId(String id, String prefix, String subject, String targetId,
                                         List<TestValidationIssueResponse> issues, Set<String> seenIds) {
        if (id.isBlank()) {
            error(issues, prefix + "-blank-id", "Cấu trúc ID", null,
                    subject + " thiếu ID định danh.", targetId);
        } else if (!seenIds.add(id)) {
            error(issues, prefix + "-duplicate-id", "Cấu trúc ID", null,
                    subject + " có ID bị trùng lặp: " + id, targetId);
        }
    }

    private static List<Map<String, Object>> passages(Map<String, Object> content) {
        var values = maps(content.get("passages"));
        if (!values.isEmpty()) {
            return values;
        }
        var rootGroups = maps(content.get("questionGroups"));
        if (rootGroups.isEmpty()) {
            return List.of();
        }
        var legacy = new LinkedHashMap<String, Object>();
        legacy.put("id", "legacy-passage-1");
        legacy.put("passageNo", 1);
        legacy.put("title", text(content.get("title"), "Reading Passage 1"));
        legacy.put("content", legacyPassageContent(content));
        legacy.put("questionGroups", rootGroups);
        return List.of(legacy);
    }

    private static String legacyPassageContent(Map<String, Object> content) {
        Object value = content.get("passageContent");
        if (value instanceof Map<?, ?> values) {
            return text(values.get("1"));
        }
        return text(value);
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static Object solutionValue(Map<String, Object> nested, Map<String, Object> question, String key) {
        return nested.containsKey(key) ? nested.get(key) : question.get(key);
    }

    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> map) {
                var normalized = new LinkedHashMap<String, Object>();
                map.forEach((key, itemValue) -> normalized.put(String.valueOf(key), itemValue));
                result.add(normalized);
            }
        }
        return result;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(item -> item != null && !item.toString().isBlank())
                .map(item -> item.toString().trim())
                .toList();
    }

    private static boolean containsBlank(Object value) {
        if (!(value instanceof List<?> values)) {
            return false;
        }
        return values.stream().anyMatch(item -> item == null || item.toString().isBlank());
    }

    private static String normalizeAnswer(String value) {
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String normalized(Object value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private static String plainText(Object value) {
        return HtmlSanitizer.stripToPlainText(text(value));
    }

    private static String text(Object value) {
        return text(value, "");
    }

    private static String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String result = value.toString().trim();
        return result.isEmpty() ? fallback : result;
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Integer nullableInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI parsed = URI.create(value);
            return parsed.getHost() != null
                    && ("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean positiveDecimal(Object value) {
        if (value == null) {
            return true;
        }
        try {
            return new BigDecimal(value.toString()).compareTo(BigDecimal.ZERO) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(value.toString());
    }

    private static void error(List<TestValidationIssueResponse> issues, String id, String sectionTitle,
                              Integer questionNo, String message, String targetId) {
        issues.add(new TestValidationIssueResponse(id, "ERROR", sectionTitle, questionNo, message, targetId));
    }
}
