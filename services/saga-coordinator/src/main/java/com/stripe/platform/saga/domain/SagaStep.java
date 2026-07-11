package com.stripe.platform.saga.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "saga_steps", schema = "saga")
@Getter
@NoArgsConstructor
public class SagaStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "step_name", nullable = false, length = 50)
    private String stepName;

    @Column(nullable = false, length = 20)
    private String status; // STARTED, COMPLETED, FAILED

    @Type(JsonbType.class)
    @Column(columnDefinition = "jsonb")
    private String payload; // optional detail about this step

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SagaStep of(UUID sagaId, String stepName,
                              String status, String payload) {
        SagaStep s   = new SagaStep();
        s.sagaId     = sagaId;
        s.stepName   = stepName;
        s.status     = status;
        s.payload    = payload;
        s.createdAt  = Instant.now();
        return s;
    }
}