package com.theieltsspells.learninglibrary.application.dto;

public record ContentHubSummaryResponse(
        long resources,
        long tests,
        long media,
        long awaitingReview,
        long inUse
) {}
