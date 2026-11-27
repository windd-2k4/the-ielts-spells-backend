package com.theieltsspells.testing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SpeakingPromptCacheWarmerTests {
    @Test
    void prewarmsEveryPromptInThePublishedFullMockRoute() {
        var speech = mock(SpeakingExaminerSpeechService.class);
        var warmer = new SpeakingPromptCacheWarmer(new SpeakingExaminerQuestionPlanner(new ObjectMapper()), speech);
        String content = """
                {"parts":[
                  {"partNo":1,"questions":[
                    {"promptText":"One?"},{"promptText":"Two?"},{"promptText":"Three?"}]},
                  {"partNo":2,"cueCardPromptHtml":"Describe a place","followUpQuestions":[{"promptText":"Why?"}]},
                  {"partNo":3,"questions":[{"promptText":"How do cities change?"}]}
                ]}
                """;

        warmer.warm(new SpeakingTestPublishedEvent(UUID.randomUUID(), content));

        verify(speech, times(9)).promptAudio(org.mockito.ArgumentMatchers.anyString());
    }
}
