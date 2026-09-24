package com.theieltsspells.billing.domain;

public enum InvoiceErrorCategory {
    RETRYABLE,
    NON_RETRYABLE,
    REQUIRES_ACTION,
    UNDETERMINED,
    RECONCILABLE
}
