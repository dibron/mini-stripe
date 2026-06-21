package com.stripe.platform.ledger.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import java.time.Instant;
import java.util.UUID;

/**
 * The event store record — append-only, never updated.
 *
 * KEY INSIGHT: occurred_at vs recorded_at
 *   occurred_at = when it happened in the business domain (set by producer)
 *   recorded_at = when WE wrote it (set by this service)
 * These diverge when Kafka lag exists or events are replayed from the outbox.
 * For point-in-time balance queries, use occurred_at.
 * For debugging delivery lag, compare recorded_at - occurred_at.
 *
 * idempotency_key UNIQUE: if Kafka delivers the same event twice (at-least-once
 * delivery guarantee), the second insert fails with a unique constraint violation.
 * We catch that and treat it as "already processed" — not an error.
 */
@Entity @Table(name = "ledger_events", schema = "ledger",
    uniqueConstraints = @UniqueConstraint(name = "uq_ledger_idempotency",
        columnNames = "idempotency_key"))
@Getter @NoArgsConstructor
public class LedgerEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // BIGSERIAL — sequence guarantees ordering

    @Column(name = "aggregate_id", nullable = false)  private UUID aggregateId; // wallet_id
    @Column(name = "event_type", nullable = false)    private String eventType;
    @Column(name = "event_version", nullable = false) private String eventVersion;
    @Type(JsonbType.class)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb") private String payload;
    @Column(name = "idempotency_key", nullable = false, unique = true) private String idempotencyKey;
    @Column(name = "occurred_at", nullable = false)   private Instant occurredAt;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt;

    public static LedgerEvent of(UUID aggregateId, String eventType, String version,
                                  String payload, String idempotencyKey, Instant occurredAt) {
        LedgerEvent e = new LedgerEvent();
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.eventVersion = version;
        e.payload = payload;
        e.idempotencyKey = idempotencyKey;
        e.occurredAt = occurredAt;
        e.recordedAt = Instant.now();
        return e;
    }
}
