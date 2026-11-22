package com.theieltsspells.testing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingExamStateMachineTests {
    @Test
    void allowsOnlyTheDeterministicExamRouteAndAbort() {
        SpeakingExamState[] route = {
                SpeakingExamState.CREATED, SpeakingExamState.MIC_CHECK, SpeakingExamState.IDENTITY_CHECK,
                SpeakingExamState.PART_1, SpeakingExamState.PART_2_PREPARATION, SpeakingExamState.PART_2_LONG_TURN,
                SpeakingExamState.PART_2_FOLLOW_UP, SpeakingExamState.PART_3, SpeakingExamState.CLOSING,
                SpeakingExamState.EVALUATION_QUEUED
        };
        for (int index = 0; index < route.length - 1; index++) {
            assertThat(SpeakingExamStateMachine.canTransition(route[index], route[index + 1])).isTrue();
            assertThat(SpeakingExamStateMachine.canTransition(route[index], SpeakingExamState.ABORTED)).isTrue();
        }
        assertThat(SpeakingExamStateMachine.canTransition(SpeakingExamState.PART_1, SpeakingExamState.PART_1)).isTrue();
        assertThat(SpeakingExamStateMachine.canTransition(SpeakingExamState.PART_3, SpeakingExamState.PART_3)).isTrue();
        assertThat(SpeakingExamStateMachine.canTransition(SpeakingExamState.PART_1,
                SpeakingExamState.PART_2_LONG_TURN)).isFalse();
        assertThat(SpeakingExamStateMachine.canTransition(SpeakingExamState.EVALUATION_QUEUED,
                SpeakingExamState.ABORTED)).isFalse();
    }
}
