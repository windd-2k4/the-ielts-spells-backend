package com.theieltsspells.billing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseSummaryDto(
        UUID id,
        String code,
        String name,
        BigDecimal tuitionAmount,
        String level
) {}
