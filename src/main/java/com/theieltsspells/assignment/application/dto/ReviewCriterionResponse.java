package com.theieltsspells.assignment.application.dto;

import java.math.BigDecimal;

public record ReviewCriterionResponse(
        String criterion,
        BigDecimal score,
        String feedback,
        int sortOrder
) {}
