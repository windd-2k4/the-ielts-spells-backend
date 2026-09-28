package com.theieltsspells.billing.domain;

public enum InvoiceStatus {
    PENDING_ISSUE,
    PILOT_PENDING_APPROVAL,
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
