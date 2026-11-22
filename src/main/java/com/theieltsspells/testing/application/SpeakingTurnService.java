package com.theieltsspells.testing.application;

import com.theieltsspells.testing.application.dto.SpeakingExaminerTurnSubmissionResponse;
import com.theieltsspells.testing.application.dto.SubmitSpeakingExaminerTurnRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpeakingTurnService {
    private final SpeakingExamSessionService sessions;

    @Transactional
    public SpeakingExaminerTurnSubmissionResponse submit(UUID sessionId,
                                                          SubmitSpeakingExaminerTurnRequest request,
                                                          UUID studentId) {
        return sessions.submitTurn(sessionId, request, studentId);
    }
}
