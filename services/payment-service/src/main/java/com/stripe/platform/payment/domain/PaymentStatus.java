package com.stripe.platform.payment.domain;

import java.util.Map;
import java.util.Set;

public enum PaymentStatus {
    INITIATED, PENDING, AUTHORIZED, CAPTURED,
    CANCELLED, EXPIRED, FAILED,
    REFUND_PENDING, REFUNDED, REFUND_FAILED,
    SETTLED, DISPUTED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> LEGAL_TRANSITIONS = Map.of(
        INITIATED,      Set.of(PENDING),
        PENDING,        Set.of(AUTHORIZED, FAILED),
        AUTHORIZED,     Set.of(CAPTURED, CANCELLED, EXPIRED),
        CAPTURED,       Set.of(REFUND_PENDING, SETTLED, DISPUTED),
        REFUND_PENDING, Set.of(REFUNDED, REFUND_FAILED)
    );

    public PaymentStatus transitionTo(PaymentStatus next) {
        Set<PaymentStatus> allowed = LEGAL_TRANSITIONS.getOrDefault(this, Set.of());
        if (!allowed.contains(next)) {
            throw new IllegalPaymentTransitionException(
                String.format("Illegal transition: %s → %s. Allowed: %s",
                    this, next, allowed.isEmpty() ? "none (terminal)" : allowed));
        }
        return next;
    }

    public boolean isTerminal() {
        return !LEGAL_TRANSITIONS.containsKey(this);
    }
}
