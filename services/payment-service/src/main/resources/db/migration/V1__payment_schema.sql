-- =============================================================================
-- Payment Service Schema — V1
-- Schema: payment (isolated from other services)
-- =============================================================================

CREATE TABLE payment.payments (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(255) NOT NULL,
    wallet_id        UUID NOT NULL,
    merchant_id      UUID NOT NULL,
    amount_cents     BIGINT NOT NULL CHECK (amount_cents > 0),
    currency         VARCHAR(3) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'INITIATED'
                     CHECK (status IN (
                         'INITIATED','PENDING','AUTHORIZED','CAPTURED',
                         'CANCELLED','EXPIRED','FAILED',
                         'REFUND_PENDING','REFUNDED','REFUND_FAILED',
                         'SETTLED','DISPUTED'
                     )),
    failure_reason   VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version          BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_payments_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_payments_wallet_id ON payment.payments(wallet_id);
CREATE INDEX idx_payments_status    ON payment.payments(status);
CREATE INDEX idx_payments_created   ON payment.payments(created_at DESC);

-- Outbox table (Phase 2 — transactional outbox pattern)
-- Relay job polls this table and publishes to Kafka.
CREATE TABLE payment.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(50) NOT NULL DEFAULT 'Payment',
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,          -- NULL = not yet published
    published       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_outbox_unpublished ON payment.outbox_events(published, created_at)
    WHERE published = FALSE;
