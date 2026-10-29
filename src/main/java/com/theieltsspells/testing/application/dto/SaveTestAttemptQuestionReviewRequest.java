package com.theieltsspells.testing.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveTestAttemptQuestionReviewRequest(
        @NotBlank
        @Pattern(regexp = "UNKNOWN_VOCABULARY|LOST_FOCUS|DISTRACTOR|SPELLING|MISHEARD|QUESTION_MISUNDERSTOOD|OTHER")
        String reasonCode,
        @Size(max = 1000) String note,
        boolean needsRetry,
        @Size(max = 200) String vocabularyTerm,
        @Size(max = 500) String vocabularyMeaning
) {
}
