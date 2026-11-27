package com.theieltsspells.testing.domain;

/** Pure deterministic lifecycle rules for an AI Speaking Examiner session. */
public final class SpeakingExamStateMachine {
    private SpeakingExamStateMachine() {}

    public static boolean canTransition(SpeakingSessionVariant variant, SpeakingExamState from,
                                        SpeakingExamState to) {
        if (to == SpeakingExamState.ABORTED) {
            return from != SpeakingExamState.EVALUATION_QUEUED && from != SpeakingExamState.ABORTED;
        }
        return switch (variant) {
            case FULL_MOCK -> fullMockTransition(from, to);
            case PART_1 -> partOneTransition(from, to);
            case PART_2 -> partTwoTransition(from, to);
            case PART_3 -> partThreeTransition(from, to);
        };
    }

    public static void requireTransition(SpeakingSessionVariant variant, SpeakingExamState from,
                                         SpeakingExamState to) {
        if (!canTransition(variant, from, to)) {
            throw new IllegalStateException("Invalid Speaking examiner state transition for " + variant
                    + ": " + from + " -> " + to);
        }
    }

    private static boolean fullMockTransition(SpeakingExamState from, SpeakingExamState to) {
        if (from == to) return from == SpeakingExamState.PART_1 || from == SpeakingExamState.PART_3;
        return switch (from) {
            case CREATED -> to == SpeakingExamState.MIC_CHECK;
            case MIC_CHECK -> to == SpeakingExamState.IDENTITY_CHECK;
            case IDENTITY_CHECK -> to == SpeakingExamState.PART_1;
            case PART_1 -> to == SpeakingExamState.PART_2_PREPARATION;
            case PART_2_PREPARATION -> to == SpeakingExamState.PART_2_LONG_TURN;
            case PART_2_LONG_TURN -> to == SpeakingExamState.PART_2_FOLLOW_UP;
            case PART_2_FOLLOW_UP -> to == SpeakingExamState.PART_3;
            case PART_3 -> to == SpeakingExamState.CLOSING;
            case CLOSING -> to == SpeakingExamState.EVALUATION_QUEUED;
            case EVALUATION_QUEUED, ABORTED -> false;
        };
    }

    private static boolean partOneTransition(SpeakingExamState from, SpeakingExamState to) {
        return switch (from) {
            case CREATED -> to == SpeakingExamState.MIC_CHECK;
            case MIC_CHECK -> to == SpeakingExamState.PART_1;
            case PART_1 -> to == SpeakingExamState.PART_1 || to == SpeakingExamState.CLOSING;
            case CLOSING -> to == SpeakingExamState.EVALUATION_QUEUED;
            default -> false;
        };
    }

    private static boolean partTwoTransition(SpeakingExamState from, SpeakingExamState to) {
        return switch (from) {
            case CREATED -> to == SpeakingExamState.MIC_CHECK;
            case MIC_CHECK -> to == SpeakingExamState.PART_2_PREPARATION;
            case PART_2_PREPARATION -> to == SpeakingExamState.PART_2_LONG_TURN;
            case PART_2_LONG_TURN -> to == SpeakingExamState.PART_2_FOLLOW_UP;
            case PART_2_FOLLOW_UP -> to == SpeakingExamState.CLOSING;
            case CLOSING -> to == SpeakingExamState.EVALUATION_QUEUED;
            default -> false;
        };
    }

    private static boolean partThreeTransition(SpeakingExamState from, SpeakingExamState to) {
        return switch (from) {
            case CREATED -> to == SpeakingExamState.MIC_CHECK;
            case MIC_CHECK -> to == SpeakingExamState.PART_3;
            case PART_3 -> to == SpeakingExamState.PART_3 || to == SpeakingExamState.CLOSING;
            case CLOSING -> to == SpeakingExamState.EVALUATION_QUEUED;
            default -> false;
        };
    }
}
