# mini-stripe

> A production-grade distributed payment platform — built from scratch to understand how Stripe actually works under the hood.

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_15-316192?style=flat&logo=postgresql&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache_Kafka-231F20?style=flat&logo=apachekafka&logoColor=white)
![Redis](https://img.shields.io/badge/Redis_7-DC382D?style=flat&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat&logo=gradle&logoColor=white)

---

## What This Is

Not a tutorial project. Not a CRUD app with a payment label slapped on it.

This is a collaborative distributed systems build between two engineers — each owning two services — designed to force real engineering decisions: how do you keep money consistent when three databases are involved? How do you guarantee a Kafka message is never lost? How do you reverse a payment across services when there's no distributed rollback?

Every line of code was written after first answering *why* — not just *how*.

---

## Architecture

```
                        ┌─────────────────────────────────────────────────┐
                        │               mini-stripe platform               │
                        └─────────────────────────────────────────────────┘

   Client                                                        Infrastructure
   ──────                                                        ──────────────
   Postman / API  ──►  [payment-service :8081]                  PostgreSQL :5432
                              │                                    ├─ payment schema
                              │  orchestrates via saga             ├─ wallet schema
                              ▼                                    ├─ ledger schema
                   [saga-coordinator :8084] ─────────────────────►└─ saga schema
                        │          │
              ┌─────────┘          └──────────┐                  Apache Kafka :29092
              ▼                               ▼                    ├─ payment.events
   [wallet-service :8082]        [ledger-service :8083]           └─ wallet.events
   double-entry accounting       append-only event store
                                                                  Redis :6379
                                                                  rate limits / locks
```

---

## The Four Services

### `payment-service` — Port 8081
Owns the full lifecycle of a payment from initiation to settlement.

- **State machine** enforced at the domain level — `INITIATED → PENDING → AUTHORIZED → CAPTURED → SETTLED`. Illegal transitions throw `IllegalPaymentTransitionException` before touching the database.
- **Optimistic locking** via `@Version` column — two concurrent captures on the same payment = one succeeds, one gets `OptimisticLockException`. No data corruption, no deadlocks.
- **Idempotency** — `UNIQUE` constraint on `idempotency_key`. Retry the same request 100 times, charge the customer once.
- **Transactional outbox** table ready for Phase 2 — payment state changes and Kafka messages written atomically. Zero message loss even on crash.

```
INITIATED → PENDING → AUTHORIZED → CAPTURED → SETTLED
                               ↘              ↘
                            CANCELLED      REFUND_PENDING → REFUNDED
                 PENDING → FAILED
```

### `wallet-service` — Port 8082
Double-entry accounting — the same system banks have used since 1494.

- Every money movement produces **two ledger entries** (DEBIT + CREDIT) inside one `@Transactional`. The database either records both or neither.
- `running_balance` stored on each entry — O(1) balance reads, no full table scan.
- Insufficient funds check happens inside the transaction — race condition safe.
- Full history endpoint returns the complete money trail for any wallet.

```
POST /api/v1/wallets                       → create wallet
GET  /api/v1/wallets/{id}/balance          → current balance (O(1))
POST /api/v1/wallets/{id}/debit            → debit with idempotency
POST /api/v1/wallets/{id}/credit           → credit (compensation path)
GET  /api/v1/wallets/{id}/history          → full ledger entry trail
```

### `ledger-service` — Port 8083
An immutable, append-only financial audit log. Rows are never updated or deleted.

- **Event sourcing** — stores `FundsDebited` / `FundsCredited` events, not balances. Balance is always derived by replaying events.
- **Point-in-time queries** — `GET /balance?at=2024-01-05T00:00:00Z` replays only events where `occurred_at ≤ cutoff`. Full financial history, no approximations.
- **`occurred_at` vs `recorded_at`** — two timestamps because Kafka lag is real. Business truth (when it happened) is separated from system truth (when we wrote it). Historical balance queries use `occurred_at`.
- **JSONB payload** with custom Hibernate `UserType` — Postgres rejects `VARCHAR → JSONB` casts in prepared statements. Solved with `Types.OTHER` in `nullSafeSet()`.

### `saga-coordinator` — Port 8084
Orchestrates the full cross-service payment flow. Replaces what a database transaction would do — but across three independent databases.

- Full **Saga state machine** with happy path + compensation path.
- Every state transition is written to `saga_steps` before the next call — **crash-safe** by design. Coordinator restart resumes from last persisted state.
- **Compensation ≠ rollback** — if the wallet was debited before a later step failed, compensation inserts a new CREDIT entry. Both entries stay in history. This is how real banking works.

```
Happy:        STARTED → PAYMENT_INITIATED → WALLET_DEBITED → LEDGER_RECORDED → PAYMENT_CAPTURED → COMPLETED
Compensation: COMPENSATION_STARTED → WALLET_CREDITED → PAYMENT_VOIDED → COMPENSATED
```

---

## Database Design

One PostgreSQL instance. Four isolated schemas — each service owns its schema and its users can only access their own schema (`payment_svc` cannot query `wallet.*`).

```
stripe_platform (database)
├── payment schema    → payments, outbox_events
├── wallet schema     → wallets, transactions, ledger_entries
├── ledger schema     → ledger_events
└── saga schema       → sagas, saga_steps
```

**Key decisions:**
- Money stored as `BIGINT` cents — never `DECIMAL` or `FLOAT`. `0.1 + 0.2 = 0.30000000000000004` in floating point.
- `TIMESTAMPTZ` everywhere — timezone-aware, stored as UTC. Required for cross-timezone settlement.
- `BIGSERIAL` for event tables where ordering matters. `UUID` for business entities where global uniqueness matters.
- Flyway manages all schema migrations — no manual DDL, no `ddl-auto: create`.

---

## Engineering Principles

| Principle | How it's enforced |
|---|---|
| **Money is never lost** | Double-entry ledger + idempotency keys at every write |
| **Every operation is idempotent** | `UNIQUE (idempotency_key)` + pre-insert check at service layer |
| **Every saga is compensatable** | Compensation steps defined before happy path was built |
| **Every failure is observable** | Actuator + Prometheus metrics on all services |
| **Every decision is an ADR** | `docs/ADRs/` — we document *why*, not just *what* |

---

## Phase Progress

| Phase | Theme | Status |
|---|---|---|
| **Phase 1** | Domain modeling, state machines, double-entry schema | ✅ Complete |
| **Phase 2** | Outbox pattern, Kafka producers and consumers | 🔵 Next |
| **Phase 3** | CQRS, full event sourcing, saga automation | ⬜ Planned |
| **Phase 4** | Redis, Kubernetes, Prometheus + Grafana observability | ⬜ Planned |
| **Phase 5** | Chaos engineering, fault tolerance, settlement | ⬜ Planned |

---

## Local Dev

**Prerequisites:** Docker Desktop, Java 21, IntelliJ IDEA

```bash
# 1. Start all infrastructure
docker-compose up -d

# 2. Verify healthy
docker-compose ps

# 3. Start each service in IntelliJ
#    Open XxxApplication.java → click ▶ next to main()
#    payment-service  → LedgerServiceApplication.java  (:8081)
#    wallet-service   → WalletServiceApplication.java  (:8082)
#    ledger-service   → LedgerServiceApplication.java  (:8083)
#    saga-coordinator → SagaCoordinatorApplication.java (:8084)

# 4. Verify services
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

**Useful UIs:**
- Kafka UI → http://localhost:8080
- Redis Commander → http://localhost:8085

**Query the database directly:**
```bash
docker exec -it stripe-postgres psql -U stripe -d stripe_platform
```

---

## Engineers

| Engineer | Owns | Services |
|---|---|---|
| **Dibyansh** (Session A) | Payment + Wallet | `payment-service`, `wallet-service` |
| **Engineer B** (Session B) | Ledger + Saga | `ledger-service`, `saga-coordinator` |

---

## ADR Index

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](docs/ADRs/ADR-001-stack-choices.md) | Java 21 + Spring Boot + PostgreSQL + Kafka — why not Go, MongoDB, or RabbitMQ | Accepted |
