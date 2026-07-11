package com.stripe.platform.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "payment")
@Getter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Type(JsonbType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private Boolean published;

    public static OutboxEvent of(UUID aggregateId, String eventType, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.aggregateId    = aggregateId;
        e.aggregateType  = "Payment";
        e.eventType      = eventType;
        e.payload        = payload;
        e.createdAt      = Instant.now();
        e.published      = false;
        return e;
    }

    public void markPublished() {
        this.published   = true;
        this.publishedAt = Instant.now();
    }
}
