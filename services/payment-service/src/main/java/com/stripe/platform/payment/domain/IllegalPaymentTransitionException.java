package com.stripe.platform.payment.domain;

/** Domain exception for illegal state machine transitions. Return HTTP 409. */
public class IllegalPaymentTransitionException extends RuntimeException {
    public IllegalPaymentTransitionException(String message) { super(message); }
}
