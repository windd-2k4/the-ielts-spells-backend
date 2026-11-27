package com.theieltsspells.testing.application;

import com.theieltsspells.shared.application.CodedApiException;
import com.theieltsspells.testing.domain.SpeakingSessionVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SpeakingExamSessionServiceTests {
    @Mock JdbcTemplate jdbc;
    @Mock SpeakingAudioService audioService;
    @Mock SpeakingExaminerQuestionPlanner planner;
    @Mock SpeakingExaminerSpeechService speechService;
    @Mock SpeakingEvaluationService evaluationService;
    private SpeakingExamSessionService service;

    @BeforeEach
    void setUp() {
        service = new SpeakingExamSessionService(jdbc, audioService, planner, speechService, evaluationService);
    }

    @Test
    void hidesExaminerEndpointsWhileTheFeatureIsDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);

        assertThatThrownBy(() -> service.get(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(CodedApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(404);
                    assertThat(exception.code()).isEqualTo("AI_SPEAKING_EXAMINER_DISABLED");
                });
        verifyNoInteractions(jdbc, audioService, planner);
    }

    @Test
    void rejectsStandalonePartTwoWhileItsFeatureFlagIsDisabled() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "partTwoEnabled", false);

        assertThatThrownBy(() -> service.createOrResume(
                UUID.randomUUID(), UUID.randomUUID(), SpeakingSessionVariant.PART_2))
                .isInstanceOfSatisfying(CodedApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(422);
                    assertThat(exception.code()).isEqualTo("SPEAKING_PART_2_DISABLED");
                });
        verifyNoInteractions(jdbc, audioService, planner);
    }

    @Test
    void rejectsStandalonePartThreeWhileItsFeatureFlagIsDisabled() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "partThreeEnabled", false);

        assertThatThrownBy(() -> service.createOrResume(
                UUID.randomUUID(), UUID.randomUUID(), SpeakingSessionVariant.PART_3))
                .isInstanceOfSatisfying(CodedApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(422);
                    assertThat(exception.code()).isEqualTo("SPEAKING_PART_3_DISABLED");
                });
        verifyNoInteractions(jdbc, audioService, planner);
    }
}
