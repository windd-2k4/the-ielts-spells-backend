package com.theieltsspells.testing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingExamStateMachineTests {
    @Test
    void keepsTheFullMockOnItsCompleteRoute() {
        assertRoute(SpeakingSessionVariant.FULL_MOCK, new SpeakingExamState[]{
                SpeakingExamState.CREATED, SpeakingExamState.MIC_CHECK, SpeakingExamState.IDENTITY_CHECK,
                SpeakingExamState.PART_1, SpeakingExamState.PART_2_PREPARATION, SpeakingExamState.PART_2_LONG_TURN,
                SpeakingExamState.PART_2_FOLLOW_UP, SpeakingExamState.PART_3, SpeakingExamState.CLOSING,
                SpeakingExamState.EVALUATION_QUEUED
        });
        assertThat(SpeakingExamStateMachine.canTransition(SpeakingSessionVariant.FULL_MOCK,
                SpeakingExamState.PART_1, SpeakingExamState.CLOSING)).isFalse();
    }

    @Test
    void allowsOnlyTheRouteOwnedByEachStandalonePart() {
        assertRoute(SpeakingSessionVariant.PART_1, new SpeakingExamState[]{
                SpeakingExamState.CREATED, SpeakingExamState.MIC_CHECK, SpeakingExamState.PART_1,
                SpeakingExamState.CLOSING, SpeakingExamState.EVALUATION_QUEUED
        });
        assertRoute(SpeakingSessionVariant.PART_2, new SpeakingExamState[]{
                SpeakingExamState.CREATED, SpeakingExamState.MIC_CHECK, SpeakingExamState.PART_2_PREPARATION,
                SpeakingExamState.PART_2_LONG_TURN, SpeakingExamState.PART_2_FOLLOW_UP,
                SpeakingExamState.CLOSING, SpeakingExamState.EVALUATION_QUEUED
        });
        assertRoute(SpeakingSessionVariant.PART_3, new SpeakingExamState[]{
                SpeakingExamState.CREATED, SpeakingExamState.MIC_CHECK, SpeakingExamState.PART_3,
                SpeakingExamState.CLOSING, SpeakingExamState.EVALUATION_QUEUED
        });

        assertThat(SpeakingExamStateMachine.canTransition(SpeakingSessionVariant.PART_1,
                SpeakingExamState.MIC_CHECK, SpeakingExamState.IDENTITY_CHECK)).isFalse();
        assertThat(SpeakingExamStateMachine.canTransition(SpeakingSessionVariant.PART_2,
                SpeakingExamState.PART_2_FOLLOW_UP, SpeakingExamState.PART_3)).isFalse();
        assertThat(SpeakingExamStateMachine.canTransition(SpeakingSessionVariant.PART_3,
                SpeakingExamState.PART_3, SpeakingExamState.PART_3)).isTrue();
    }

    private void assertRoute(SpeakingSessionVariant variant, SpeakingExamState[] route) {
        for (int index = 0; index < route.length - 1; index++) {
            assertThat(SpeakingExamStateMachine.canTransition(variant, route[index], route[index + 1])).isTrue();
            assertThat(SpeakingExamStateMachine.canTransition(variant, route[index], SpeakingExamState.ABORTED)).isTrue();
        }
        assertThat(SpeakingExamStateMachine.canTransition(variant, SpeakingExamState.EVALUATION_QUEUED,
                SpeakingExamState.ABORTED)).isFalse();
    }
}
