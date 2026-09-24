package com.theieltsspells.billing.application.dto;

public record StaticQrResponse(
        String qrCodeUrl,
        String accountNumber,
        String bankName,
        String accountName,
        String suggestedTransferContent,
        String productName
) {}
