package com.theieltsspells.academic.application;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingCourseView(
        UUID id,
        String code,
        String name,
        BigDecimal tuitionAmount,
        String level,
        boolean active
) {}
