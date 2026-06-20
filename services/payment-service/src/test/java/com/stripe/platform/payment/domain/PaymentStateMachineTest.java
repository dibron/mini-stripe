package com.stripe.platform.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/**
 * State machine tests — the most important tests in this service.
 * Every legal transition must work. Every illegal transition must throw.
 * If these tests pass, money cannot move in an illegal direction.
 */
class PaymentStateMachineTest {

    @Test
    void happyPath_initiateToSettled() {
        Payment p = Payment.initiate(UUID.randomUUID(), UUID.randomUUID(),
                                     5000L, "USD", "key-001");
        p.submitToPending();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);

        p.authorize();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);

        p.capture();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CAPTURED);

        p.settle();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(p.getStatus().isTerminal()).isTrue();
    }

    @Test
    void happyPath_captureToRefunded() {
        Payment p = makeCapture();
        p.requestRefund();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        p.completeRefund();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(p.getStatus().isTerminal()).isTrue();
    }

    @Test
    void illegalTransition_cannotCaptureFailedPayment() {
        Payment p = Payment.initiate(UUID.randomUUID(), UUID.randomUUID(),
                                     5000L, "USD", "key-002");
        p.submitToPending();
        p.fail("Insufficient funds");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);

        // This must throw — you cannot capture a failed payment
        assertThatThrownBy(p::capture)
            .isInstanceOf(IllegalPaymentTransitionException.class)
            .hasMessageContaining("FAILED");
    }

    @Test
    void illegalTransition_cannotDoubleCapture() {
        Payment p = makeCapture();
        assertThatThrownBy(p::capture)
            .isInstanceOf(IllegalPaymentTransitionException.class);
    }

    @Test
    void illegalTransition_cannotRefundUnlessCapture() {
        Payment p = Payment.initiate(UUID.randomUUID(), UUID.randomUUID(),
                                     5000L, "USD", "key-003");
        p.submitToPending();
        p.authorize();
        // AUTHORIZED → REFUND_PENDING is illegal (not captured yet)
        assertThatThrownBy(p::requestRefund)
            .isInstanceOf(IllegalPaymentTransitionException.class);
    }

    @Test
    void terminalStates_noFurtherTransitions() {
        Payment settled = makeCapture();
        settled.settle();
        assertThat(settled.getStatus().isTerminal()).isTrue();
        assertThatThrownBy(settled::capture).isInstanceOf(IllegalPaymentTransitionException.class);
        assertThatThrownBy(settled::authorize).isInstanceOf(IllegalPaymentTransitionException.class);
    }

    @Test
    void moneyStoredAsCents() {
        Payment p = Payment.initiate(UUID.randomUUID(), UUID.randomUUID(),
                                     5000L, "USD", "key-004");
        assertThat(p.getAmountCents()).isEqualTo(5000L); // $50.00
    }

    private Payment makeCapture() {
        Payment p = Payment.initiate(UUID.randomUUID(), UUID.randomUUID(),
                                     5000L, "USD", "key-cap");
        p.submitToPending();
        p.authorize();
        p.capture();
        return p;
    }
}
