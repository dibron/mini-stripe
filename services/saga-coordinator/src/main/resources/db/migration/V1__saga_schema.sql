CREATE TABLE saga.sagas (
    id             UUID PRIMARY KEY,
    payment_id     UUID NOT NULL,
    wallet_id      UUID NOT NULL,
    amount_cents   BIGINT NOT NULL,
    currency       VARCHAR(3) NOT NULL,
    status         VARCHAR(30) NOT NULL DEFAULT 'STARTED',
    failure_reason JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at   TIMESTAMPTZ,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sagas_status     ON saga.sagas(status);
CREATE INDEX idx_sagas_payment_id ON saga.sagas(payment_id);

-- Step-level audit trail: every state transition is recorded here.
CREATE TABLE saga.saga_steps (
    id          BIGSERIAL PRIMARY KEY,
    saga_id     UUID NOT NULL REFERENCES saga.sagas(id),
    step_name   VARCHAR(50) NOT NULL,
    status      VARCHAR(20) NOT NULL CHECK (status IN ('STARTED','COMPLETED','FAILED')),
    payload     JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
