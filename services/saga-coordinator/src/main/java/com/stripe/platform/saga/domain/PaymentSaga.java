package com.stripe.platform.saga.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment Saga — orchestrates the cross-service payment flow.
 *
 * SAGA vs TRANSACTION:
 * A database transaction is atomic at the DB level. A saga is "atomic" at the
 * business level — if any step fails, we run compensation steps to undo
 * what already succeeded.
 *
 * COMPENSATION vs ROLLBACK:
 * Rollback undoes a DB change (it never happened). Compensation acknowledges
 * the change happened and creates a new change to reverse it.
 * Example: wallet was debited → compensation is a CREDIT (not an undo).
 *
 * The @Version field prevents two threads from advancing the same saga simultaneously.
 */
@Entity @Table(name = "sagas", schema = "saga")
@Getter @NoArgsConstructor
public class PaymentSaga {
    @Id private UUID id;

    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "wallet_id", nullable = false)  private UUID walletId;
    @Column(name = "amount_cents", nullable = false) private Long amountCents;
    @Column(nullable = false, length = 3)          private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)         private SagaStatus status;

    @Column(columnDefinition = "jsonb")            private String failureReason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at")                 private Instant completedAt;

    @Version private Long version;

    public static PaymentSaga start(UUID paymentId, UUID walletId,
                                    Long amountCents, String currency) {
        PaymentSaga s = new PaymentSaga();
        s.id = UUID.randomUUID();
        s.paymentId = paymentId;
        s.walletId = walletId;
        s.amountCents = amountCents;
        s.currency = currency;
        s.status = SagaStatus.STARTED;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    public void advance(SagaStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
        if (next == SagaStatus.COMPLETED || next == SagaStatus.COMPENSATED
                || next == SagaStatus.FAILED) {
            this.completedAt = Instant.now();
        }
    }
}
