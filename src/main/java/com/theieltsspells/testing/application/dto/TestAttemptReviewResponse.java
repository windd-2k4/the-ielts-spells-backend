package com.theieltsspells.testing.application.dto;

import java.util.List;

public record TestAttemptReviewResponse(
        TestAttemptSummaryResponse attempt,
        List<TestAttemptSummaryResponse> attemptHistory,
        StudentReadingAttemptResponse attemptPayload,
        ReadingAttemptResultResponse result,
        List<TestAttemptQuestionReviewResponse> reviews
) {
}
