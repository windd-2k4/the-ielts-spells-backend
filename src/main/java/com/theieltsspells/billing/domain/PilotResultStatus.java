package com.theieltsspells.billing.domain;

/**
 * Lifecycle states of an end-to-end controlled Pilot execution.
 */
public enum PilotResultStatus {
    NOT_STARTED,
    IN_PROGRESS,
    PASSED,
    FAILED,
    REQUIRES_REVIEW
}
