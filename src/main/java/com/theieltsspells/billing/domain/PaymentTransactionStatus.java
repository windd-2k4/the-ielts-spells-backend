package com.theieltsspells.billing.domain;

public enum PaymentTransactionStatus {
    SUCCESS,
    UNDERPAID,
    OVERPAID,
    UNMATCHED,
    REFUNDED
}
