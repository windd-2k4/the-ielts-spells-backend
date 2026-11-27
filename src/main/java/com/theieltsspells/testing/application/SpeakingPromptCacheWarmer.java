package com.theieltsspells.testing.application;

import com.theieltsspells.testing.domain.SpeakingSessionVariant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
class SpeakingPromptCacheWarmer {
    private final SpeakingExaminerQuestionPlanner planner;
    private final SpeakingExaminerSpeechService speechService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void warm(SpeakingTestPublishedEvent event) {
        long started = System.nanoTime();
        try {
            var prompts = planner.plan(event.builderContent(), SpeakingSessionVariant.FULL_MOCK).turns().stream()
                    .map(SpeakingExaminerQuestionPlanner.PlannedTurn::speechText)
                    .distinct()
                    .toList();
            prompts.forEach(speechService::promptAudio);
            log.info("event=speaking_tts_prewarm_completed testVersionId={} promptCount={} elapsedMs={}",
                    event.testVersionId(), prompts.size(), (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException exception) {
            log.warn("event=speaking_tts_prewarm_failed testVersionId={} elapsedMs={} reason={}",
                    event.testVersionId(), (System.nanoTime() - started) / 1_000_000, exception.getMessage());
        }
    }
}
