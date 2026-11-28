package com.theieltsspells.testing.application;

import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnSubmissionResponse;
import com.theieltsspells.testing.application.dto.SubmitSpeakingExaminerTurnRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates turn submission without holding a DB transaction or row lock across STT, LLM, or TTS calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakingTurnService {
    private final SpeakingExamSessionService sessions;
    private final SpeakingExaminerSpeechService speechService;
    private final SpeakingAdaptiveFollowUpService adaptiveFollowUpService;

    public SpeakingExaminerTurnSubmissionResponse submit(UUID sessionId,
                                                         SubmitSpeakingExaminerTurnRequest request,
                                                         UUID studentId) {
        SpeakingExamSessionService.TurnPreparation prepared = sessions.prepareTurn(sessionId, request, studentId);
        if (prepared.alreadyAccepted() != null) {
            return prepared.alreadyAccepted();
        }
        long turnStarted = System.nanoTime();
        if (prepared.transcriptionMode() != SpeakingExamSessionService.TranscriptionMode.SYNCHRONOUS) {
            return sessions.commitTurn(
                    sessionId,
                    request,
                    studentId,
                    prepared.durationMs(),
                    null,
                    prepared.transcriptionMode(),
                    0L,
                    0L,
                    0L,
                    0L,
                    turnStarted,
                    null,
                    null);
        }

        var timedTranscription = speechService.transcribeTimed(request.storageKey());
        String transcript = timedTranscription.transcription().text().trim();

        String adaptivePrompt = null;
        String adaptiveAudioCacheKey = null;
        long followUpGenerationMs = 0L;
        long ttsProcessingMs = 0L;

        if (prepared.adaptiveContext() != null) {
            long followUpStarted = System.nanoTime();
            var context = prepared.adaptiveContext().withStudentTranscript(transcript);
            var proposal = adaptiveFollowUpService.propose(context);
            followUpGenerationMs = (System.nanoTime() - followUpStarted) / 1_000_000;
            if (proposal.isPresent()) {
                adaptivePrompt = proposal.get().questionText();
                long ttsStarted = System.nanoTime();
                try {
                    adaptiveAudioCacheKey = speechService.promptAudio(adaptivePrompt).cacheKey();
                } catch (RuntimeException ex) {
                    log.warn("Adaptive follow-up TTS failed; falling back to banked route: {}", ex.getMessage());
                    adaptivePrompt = null;
                    adaptiveAudioCacheKey = null;
                } finally {
                    ttsProcessingMs = (System.nanoTime() - ttsStarted) / 1_000_000;
                }
            }
        }

        return sessions.commitTurn(
                sessionId,
                request,
                studentId,
                prepared.durationMs(),
                transcript,
                SpeakingExamSessionService.TranscriptionMode.SYNCHRONOUS,
                timedTranscription.storageReadMs(),
                timedTranscription.sttProcessingMs(),
                followUpGenerationMs,
                ttsProcessingMs,
                turnStarted,
                adaptivePrompt,
                adaptiveAudioCacheKey);
    }
}
