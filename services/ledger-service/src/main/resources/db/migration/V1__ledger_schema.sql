CREATE TABLE ledger.ledger_events (
    id               BIGSERIAL PRIMARY KEY,
    aggregate_id     UUID NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    event_version    VARCHAR(10) NOT NULL DEFAULT '1.0',
    payload          JSONB NOT NULL,
    idempotency_key  VARCHAR(255) NOT NULL,
    occurred_at      TIMESTAMPTZ NOT NULL,
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ledger_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_ledger_aggregate ON ledger.ledger_events(aggregate_id, id ASC);
CREATE INDEX idx_ledger_occurred  ON ledger.ledger_events(occurred_at);
