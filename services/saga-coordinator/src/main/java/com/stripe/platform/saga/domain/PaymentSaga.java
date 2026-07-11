package com.stripe.platform.saga.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import java.time.Instant;
import java.util.UUID;


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

    @Type(JsonbType.class)
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

    public void setFailureReason(String reason) {
        this.failureReason = reason;
    }
}
