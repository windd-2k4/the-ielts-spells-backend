package com.theieltsspells.testing.application.dto;

import java.util.List;

public record SpeakingHintStepResponse(
        String id,
        String title,
        String instruction,
        List<SpeakingHintOptionResponse> options
) {}
