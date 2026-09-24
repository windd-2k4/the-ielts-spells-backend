package com.theieltsspells.billing.domain;

public enum ProductionActivationState {
    SANDBOX,
    PRODUCTION_CONFIGURED,
    PRODUCTION_READY,
    PRODUCTION_PILOT,
    PRODUCTION_ACTIVE;

    public boolean isProductionContext() {
        return this != SANDBOX;
    }

    public boolean isProductionIssuanceAllowed() {
        return this == PRODUCTION_PILOT || this == PRODUCTION_ACTIVE;
    }
}
