package com.theieltsspells.billing.domain;

public enum InvoiceStatus {
    PENDING_ISSUE,
    ISSUING,
    ISSUED,
    FAILED,
    CANCELLED
}
