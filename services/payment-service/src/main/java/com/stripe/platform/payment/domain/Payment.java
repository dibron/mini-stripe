package com.stripe.platform.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment Aggregate — the single consistency boundary for payment state.
 *
 * All state transitions go through domain methods.
 * The @Version field provides optimistic locking against concurrent updates.
 * Amount is stored as Long cents — never Double (floating point precision loss).
 */
@Entity
@Table(name = "payments", schema = "payment",
    uniqueConstraints = @UniqueConstraint(name = "uq_payments_idempotency",
        columnNames = "idempotency_key"))
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    /** Stored in cents. $50.00 = 5000L. Never store money as Double. */
    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock — prevents concurrent state transitions on the same payment. */
    @Version
    @Column(nullable = false)
    private Long version;

    public static Payment initiate(UUID walletId, UUID merchantId,
                                   Long amountCents, String currency,
                                   String idempotencyKey) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.walletId = walletId;
        p.merchantId = merchantId;
        p.amountCents = amountCents;
        p.currency = currency;
        p.idempotencyKey = idempotencyKey;
        p.status = PaymentStatus.INITIATED;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    public void submitToPending()        { transition(PaymentStatus.PENDING); }
    public void authorize()              { transition(PaymentStatus.AUTHORIZED); }
    public void capture()                { transition(PaymentStatus.CAPTURED); }
    public void cancel()                 { transition(PaymentStatus.CANCELLED); }
    public void expire()                 { transition(PaymentStatus.EXPIRED); }
    public void requestRefund()          { transition(PaymentStatus.REFUND_PENDING); }
    public void completeRefund()         { transition(PaymentStatus.REFUNDED); }
    public void settle()                 { transition(PaymentStatus.SETTLED); }
    public void dispute()                { transition(PaymentStatus.DISPUTED); }

    public void fail(String reason) {
        transition(PaymentStatus.FAILED);
        this.failureReason = reason;
    }

    public void failRefund(String reason) {
        transition(PaymentStatus.REFUND_FAILED);
        this.failureReason = reason;
    }

    private void transition(PaymentStatus next) {
        this.status = this.status.transitionTo(next);
        this.updatedAt = Instant.now();
    }
}
