package com.theieltsspells.testing.application;

import com.theieltsspells.testing.infrastructure.speech.SpeachesSpeechClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.speech.warmup-enabled", havingValue = "true")
class SpeakingSpeechWarmupService {
    private final SpeachesSpeechClient speechClient;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        long started = System.nanoTime();
        try {
            speechClient.synthesize("Ready.");
            log.info("event=speaking_speech_warmup_completed elapsedMs={}",
                    (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException exception) {
            log.warn("event=speaking_speech_warmup_failed elapsedMs={} reason={}",
                    (System.nanoTime() - started) / 1_000_000, exception.getMessage());
        }
    }
}
