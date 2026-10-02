package com.theieltsspells.billing.domain;

public enum PaymentTransactionStatus {
    SUCCESS,
    STANDALONE_PAYMENT,
    PARTIAL_PAYMENT,
    UNDERPAID,
    OVERPAID,
    UNMATCHED,
    REFUNDED
}
