package com.stripe.platform.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Payment Service — owns the payment lifecycle state machine.
 *
 * Responsibilities:
 *   - Accept payment initiation requests with idempotency keys
 *   - Enforce the payment state machine (no illegal transitions)
 *   - Publish domain events to the outbox (Phase 2)
 *   - Expose compensation endpoints for the Saga Coordinator (Phase 3)
 *
 * Session A owns this service end-to-end:
 *   business logic + DB schema + Kafka config + K8s manifests + dashboards
 */
@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
