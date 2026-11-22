package com.theieltsspells.testing.domain;

/** Pure deterministic lifecycle rules for an AI Speaking Examiner session. */
public final class SpeakingExamStateMachine {
    private SpeakingExamStateMachine() {}

    public static boolean canTransition(SpeakingExamState from, SpeakingExamState to) {
        if (from == to) return from == SpeakingExamState.PART_1 || from == SpeakingExamState.PART_3;
        return switch (from) {
            case CREATED -> to == SpeakingExamState.MIC_CHECK || to == SpeakingExamState.ABORTED;
            case MIC_CHECK -> to == SpeakingExamState.IDENTITY_CHECK || to == SpeakingExamState.ABORTED;
            case IDENTITY_CHECK -> to == SpeakingExamState.PART_1 || to == SpeakingExamState.ABORTED;
            case PART_1 -> to == SpeakingExamState.PART_2_PREPARATION || to == SpeakingExamState.ABORTED;
            case PART_2_PREPARATION -> to == SpeakingExamState.PART_2_LONG_TURN || to == SpeakingExamState.ABORTED;
            case PART_2_LONG_TURN -> to == SpeakingExamState.PART_2_FOLLOW_UP || to == SpeakingExamState.ABORTED;
            case PART_2_FOLLOW_UP -> to == SpeakingExamState.PART_3 || to == SpeakingExamState.ABORTED;
            case PART_3 -> to == SpeakingExamState.CLOSING || to == SpeakingExamState.ABORTED;
            case CLOSING -> to == SpeakingExamState.EVALUATION_QUEUED || to == SpeakingExamState.ABORTED;
            case EVALUATION_QUEUED, ABORTED -> false;
        };
    }

    public static void requireTransition(SpeakingExamState from, SpeakingExamState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid Speaking examiner state transition: " + from + " -> " + to);
        }
    }
}
