package com.theieltsspells.billing.domain;

public enum InvoiceStatus {
    PENDING_ISSUE,
    CREATING,
    PROCESSING,
    DRAFT,
    ISSUING,
    ISSUED,
    FAILED,
    UNKNOWN,
    CANCELLED,
    ADJUSTED,
    REPLACED
}
