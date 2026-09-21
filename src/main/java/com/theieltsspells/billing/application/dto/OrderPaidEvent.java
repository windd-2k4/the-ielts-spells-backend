package com.theieltsspells.billing.application.dto;

import java.util.UUID;

public record OrderPaidEvent(
        UUID orderId,
        String activationToken
) {}
