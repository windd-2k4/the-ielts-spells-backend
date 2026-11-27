package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiProviderRouter;
import com.theieltsspells.shared.ai.AiTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generates bounded, Part 3-scoped adaptive follow-up questions via AiProviderRouter.
 * Always falls back to the banked route when AI is unconfigured, slow, or produces invalid output.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakingAdaptiveFollowUpService {
    private static final int MIN_TRANSCRIPT_WORDS = 6;
    private static final int MIN_QUESTION_WORDS = 5;
    private static final int MAX_QUESTION_WORDS = 32;
    private static final int MAX_QUESTION_CHARS = 220;
    private static final double MAX_JACCARD_OVERLAP = 0.75;
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9\\s]");
    private static final List<String> FORBIDDEN_PHRASES = List.of(
            "band score", "estimated band", "your score", "pronunciation", "grammar mistake",
            "grammatical error", "feedback", "good job", "well done", "great answer", "improve your"
    );

    private final AiProviderRouter router;
    private final ObjectMapper objectMapper;

    @Value("${app.speaking-examiner.adaptive-follow-up-timeout-ms:6000}")
    private long timeoutMs;

    public Optional<Proposal> propose(Context context) {
        if (context == null || context.remainingFollowUps() <= 0) return Optional.empty();
        String transcript = normalizeSpaces(context.studentTranscript());
        if (wordCount(transcript) < MIN_TRANSCRIPT_WORDS) {
            log.debug("event=speaking_adaptive_followup_skipped reason=short_transcript words={}",
                    wordCount(transcript));
            return Optional.empty();
        }

        long started = System.nanoTime();
        try {
            Duration timeout = Duration.ofMillis(Math.max(1_500L, Math.min(15_000L, timeoutMs)));
            var request = new AiGenerationRequest(
                    systemPrompt(),
                    userPrompt(context, transcript),
                    220,
                    0.35,
                    "application/json",
                    true,
                    timeout
            );
            Optional<Proposal> decoded = router.execute(AiTaskType.CHAT, request,
                    result -> decodeAndValidate(result.content(), context.existingPrompts()));
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            if (decoded.isPresent()) {
                log.info("event=speaking_adaptive_followup_generated elapsedMs={} questionLength={}",
                        elapsedMs, decoded.get().questionText().length());
            } else {
                log.info("event=speaking_adaptive_followup_declined elapsedMs={}", elapsedMs);
            }
            return decoded;
        } catch (RuntimeException exception) {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            log.warn("event=speaking_adaptive_followup_fallback elapsedMs={} reason={}",
                    elapsedMs, exception.getMessage());
            return Optional.empty();
        }
    }

    Optional<Proposal> decodeAndValidate(String rawContent, List<String> existingPrompts) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(rawContent));
            if (!root.path("shouldFollowUp").asBoolean(false)) return Optional.empty();
            String cleaned = cleanQuestion(root.path("questionText").asText(""));
            if (!isValidQuestion(cleaned, existingPrompts)) return Optional.empty();
            String rationale = normalizeSpaces(root.path("rationale").asText(""));
            return Optional.of(new Proposal(cleaned, rationale));
        } catch (Exception exception) {
            log.warn("event=speaking_adaptive_followup_invalid_json reason={}", exception.getMessage());
            return Optional.empty();
        }
    }

    boolean isValidQuestion(String question, List<String> existingPrompts) {
        if (question.isBlank() || question.length() > MAX_QUESTION_CHARS) return false;
        int words = wordCount(question);
        if (words < MIN_QUESTION_WORDS || words > MAX_QUESTION_WORDS) return false;
        if (!question.endsWith("?")) return false;
        long questionMarks = question.chars().filter(ch -> ch == '?').count();
        if (questionMarks != 1) return false;

        String lower = question.toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_PHRASES) {
            if (lower.contains(forbidden)) return false;
        }

        if (existingPrompts != null) {
            String normalizedCandidate = canonicalTokens(lower);
            Set<String> candidateWords = wordSet(lower);
            for (String existing : existingPrompts) {
                if (existing == null || existing.isBlank()) continue;
                String existingLower = existing.toLowerCase(Locale.ROOT);
                if (canonicalTokens(existingLower).equals(normalizedCandidate)) return false;
                if (jaccard(candidateWords, wordSet(existingLower)) >= MAX_JACCARD_OVERLAP) return false;
            }
        }
        return true;
    }

    private String systemPrompt() {
        return """
                You are an IELTS Speaking Part 3 Examiner conducting an academic two-way discussion.
                Decide whether to ask ONE brief, natural follow-up question probing reasons, examples,
                consequences, comparisons, or an alternative perspective directly connected to the candidate's
                most recent answer (and Part 2 topic if present).
                Strict rules:
                - Ask at most ONE concise open-ended question (6 to 28 words, <= 180 characters, ending with '?').
                - Do NOT repeat or paraphrase any existing question in the session.
                - Do NOT give praise, feedback, corrections, scores, or advice.
                - If the candidate's answer is already exhausted or unclear, set shouldFollowUp to false.
                Respond ONLY with JSON:
                {"shouldFollowUp": true, "questionText": "...", "rationale": "brief reason"}
                """;
    }

    private String userPrompt(Context context, String transcript) {
        StringBuilder builder = new StringBuilder();
        builder.append("Topic: ").append(nullToNone(context.topicKey())).append('\n');
        builder.append("Remaining adaptive follow-up quota: ").append(context.remainingFollowUps()).append('\n');
        if (context.part2CuePrompt() != null && !context.part2CuePrompt().isBlank()) {
            builder.append("Part 2 Cue Card: ").append(truncate(context.part2CuePrompt(), 280)).append('\n');
        }
        if (context.part2Transcript() != null && !context.part2Transcript().isBlank()) {
            builder.append("Part 2 Summary Transcript: ").append(truncate(context.part2Transcript(), 450)).append('\n');
        }
        if (context.recentPart3Exchanges() != null && !context.recentPart3Exchanges().isEmpty()) {
            builder.append("Recent Part 3 turns:\n");
            for (Exchange exchange : context.recentPart3Exchanges()) {
                builder.append("- Q: ").append(truncate(exchange.question(), 180))
                        .append(" | A: ").append(truncate(exchange.answer(), 280)).append('\n');
            }
        }
        builder.append("Current Part 3 question: ").append(context.currentQuestion()).append('\n');
        builder.append("Candidate's current answer: ").append(truncate(transcript, 650)).append('\n');
        if (context.existingPrompts() != null && !context.existingPrompts().isEmpty()) {
            builder.append("Questions already in session (must not repeat):\n");
            for (String prompt : context.existingPrompts()) {
                builder.append("- ").append(truncate(prompt, 140)).append('\n');
            }
        }
        return builder.toString();
    }

    private String cleanQuestion(String raw) {
        String text = normalizeSpaces(raw);
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            if (text.length() > 2) text = text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }

    private String normalizeSpaces(String value) {
        return value == null ? "" : MULTI_SPACE.matcher(value.trim()).replaceAll(" ");
    }

    private int wordCount(String value) {
        if (value == null || value.isBlank()) return 0;
        return normalizeSpaces(value).split(" ").length;
    }

    private String canonicalTokens(String lower) {
        return normalizeSpaces(NON_ALNUM.matcher(lower).replaceAll(" "));
    }

    private Set<String> wordSet(String lower) {
        String cleaned = canonicalTokens(lower);
        if (cleaned.isBlank()) return Set.of();
        return new HashSet<>(Arrays.asList(cleaned.split(" ")));
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0d;
        int intersection = 0;
        for (String token : left) {
            if (right.contains(token)) intersection++;
        }
        int union = left.size() + right.size() - intersection;
        return union == 0 ? 0d : (double) intersection / union;
    }

    private String truncate(String value, int maxChars) {
        String normalized = normalizeSpaces(value);
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }

    private String nullToNone(String value) {
        return value == null || value.isBlank() ? "general" : value;
    }

    public record Exchange(String question, String answer) {}

    public record Context(
            String topicKey,
            String currentQuestion,
            String studentTranscript,
            String part2CuePrompt,
            String part2Transcript,
            List<Exchange> recentPart3Exchanges,
            List<String> existingPrompts,
            int remainingFollowUps
    ) {
        public Context withStudentTranscript(String transcript) {
            return new Context(topicKey, currentQuestion, transcript, part2CuePrompt, part2Transcript,
                    recentPart3Exchanges, existingPrompts, remainingFollowUps);
        }
    }

    public record Proposal(String questionText, String rationale) {}
}
