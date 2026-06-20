CREATE TABLE wallet.wallets (
    id          UUID PRIMARY KEY,
    owner_id    UUID NOT NULL,
    currency    VARCHAR(3) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE wallet.transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(255) NOT NULL,
    type             VARCHAR(30) NOT NULL CHECK (type IN ('PAYMENT','REFUND','SETTLEMENT','ADJUSTMENT','FEE')),
    amount           BIGINT NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reference_id     UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_transactions_idempotency UNIQUE (idempotency_key)
);

-- Append-only. No UPDATE or DELETE ever.
CREATE TABLE wallet.ledger_entries (
    id               BIGSERIAL PRIMARY KEY,
    transaction_id   UUID NOT NULL REFERENCES wallet.transactions(id),
    wallet_id        UUID NOT NULL REFERENCES wallet.wallets(id),
    entry_type       VARCHAR(6) NOT NULL CHECK (entry_type IN ('DEBIT','CREDIT')),
    amount           BIGINT NOT NULL CHECK (amount > 0),
    running_balance  BIGINT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_wallet ON wallet.ledger_entries(wallet_id, id DESC);
CREATE INDEX idx_wallets_owner ON wallet.wallets(owner_id);
