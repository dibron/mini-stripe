package com.stripe.platform.saga.domain;
public enum SagaStatus {
    STARTED,
    PAYMENT_INITIATED,
    WALLET_DEBITED,
    LEDGER_RECORDED,
    PAYMENT_CAPTURED,
    COMPLETED,
    // Compensation path
    COMPENSATION_STARTED,
    WALLET_CREDITED,
    PAYMENT_VOIDED,
    COMPENSATED,
    // Terminal failure
    FAILED
}
