package com.theieltsspells.billing.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SepayWebhookPayload(
        Long id,
        String gateway,
        String transactionDate,
        String accountNumber,
        String subAccount,
        String code,
        String content,
        String transferType,
        String description,
        BigDecimal transferAmount,
        String referenceCode,
        BigDecimal accumulated
) {}
