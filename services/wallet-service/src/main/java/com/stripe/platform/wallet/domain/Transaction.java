package com.stripe.platform.wallet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/**
 * A Transaction is the business event that causes money to move.
 * Every transaction produces exactly TWO ledger entries (one DEBIT, one CREDIT).
 * The idempotency_key prevents the same transaction being processed twice.
 */
@Entity
@Table(name = "transactions", schema = "wallet",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_transactions_idempotency",
                columnNames = "idempotency_key"))
@Getter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Transaction create(String idempotencyKey, String type,
                                     Long amount, String currency,
                                     UUID referenceId) {
        Transaction t    = new Transaction();
        t.idempotencyKey = idempotencyKey;
        t.type           = type;
        t.amount         = amount;
        t.currency       = currency;
        t.status         = "COMPLETED";
        t.referenceId    = referenceId;
        t.createdAt      = Instant.now();
        return t;
    }
}