# mini-stripe — Distributed Payment Platform

A production-grade distributed payment platform built from scratch to learn and demonstrate
real-world distributed systems engineering. Modelled after how Stripe, Brex, and Adyen
architect their core payment infrastructure.

> **Built as a hands-on engineering apprenticeship** — every design decision is grounded
> in a real failure mode that actual fintech companies have encountered.

---

## What This Is

A fully functional payment processing backend with four independent microservices communicating
via Apache Kafka, backed by PostgreSQL with schema-level isolation, and coordinated by a Saga
orchestrator for distributed transaction management.

The system handles the complete lifecycle of a payment — from initiation through authorization,
capture, settlement, and refund — with guaranteed consistency across service boundaries, even
in the presence of partial failures.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENT (REST)                              │
└──────────┬──────────────────┬──────────────────┬────────────────────┘
           │                  │                  │
           ▼                  ▼                  ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ payment-service │ │ wallet-service  │ │ saga-coordinator│
│   port: 8081    │ │   port: 8082    │ │   port: 8084    │
│                 │ │                 │ │                 │
│ State machine   │ │ Double-entry    │ │ Orchestrates    │
│ 12-state FSM    │ │ accounting      │ │ cross-service   │
│ Optimistic lock │ │ Running balance │ │ flow + compen-  │
│ Outbox pattern  │ │ Idempotent debit│ │ sation          │
└────────┬────────┘ └────────┬────────┘ └────────┬────────┘
         │                   │                   │
         │         ┌─────────────────┐           │
         │         │ ledger-service  │           │
         │         │   port: 8083    │           │
         │         │                 │           │
         │         │ Event sourcing  │           │
         │         │ Point-in-time   │           │
         │         │ balance queries │           │
         │         └────────┬────────┘           │
         │                  │                    │
         └──────────────────┼────────────────────┘
                            │
              ┌─────────────▼─────────────┐
              │       Apache Kafka         │
              │  payment-events (6 parts)  │
              │  wallet-events  (6 parts)  │
              │  ledger-events  (6 parts)  │
              │  saga-commands  (4 parts)  │
              │  + Dead Letter Queues      │
              └─────────────┬─────────────┘
                            │
              ┌─────────────▼─────────────┐
              │   PostgreSQL 15            │
              │   ┌──────────────────┐     │
              │   │ payment schema   │     │
              │   │ wallet  schema   │     │
              │   │ ledger  schema   │     │
              │   │ saga    schema   │     │
              │   └──────────────────┘     │
              └───────────────────────────┘
```

---

## Technology Stack

| Layer | Technology | Why |
|---|---|---|
| Language | Java 21 | Production choice at Stripe, Brex, Adyen. Virtual threads, records, sealed types |
| Framework | Spring Boot 3.2 | Transaction management, Kafka integration, Actuator health probes |
| Database | PostgreSQL 15 | ACID transactions, MVCC, JSONB, logical replication for CDC |
| Messaging | Apache Kafka 3.6 (KRaft) | Replay, consumer group independence, partition-ordered delivery |
| Caching / Locks | Redis 7 | Distributed locks (Redlock), sliding window rate limiting, balance cache |
| Migrations | Flyway | Versioned, repeatable, environment-consistent schema evolution |
| Metrics | Micrometer + Prometheus | Service-level payment success rate, latency percentiles |
| Containers | Docker Compose | Local dev parity with production topology |
| Orchestration | Kubernetes | HPA, rolling updates, liveness/readiness probes |
| Build | Maven | Dependency management, reproducible builds |

---

## Services

### payment-service — Port 8081

Owns the complete payment lifecycle. Implements a 12-state finite state machine enforced at
the domain layer — illegal transitions throw before any database write occurs.

**Key design decisions:**

- `PaymentStatus.transitionTo()` is the only code path that changes payment state, preventing
  illegal money movement regardless of how the service is called
- `@Version` (optimistic locking) adds `AND version=N` to every UPDATE — two concurrent
  capture requests on the same payment: the first succeeds, the second gets HTTP 409
- `idempotency_key` has a UNIQUE constraint at both Java and DB level — network retries
  never create duplicate payments
- Transactional Outbox pattern (Phase 2): payment state change and Kafka event are written
  in the same `@Transactional` — Kafka being down never causes a missed event

**Payment state machine:**

```
INITIATED → PENDING → AUTHORIZED → CAPTURED → SETTLED
                  ↘ FAILED      ↘ CANCELLED  ↘ REFUND_PENDING → REFUNDED
                                ↘ EXPIRED                     ↘ REFUND_FAILED
                                             ↘ DISPUTED
```

**Endpoints:**

```
POST   /api/v1/payments                    Create payment (idempotent)
GET    /api/v1/payments/{id}               Get payment by ID
POST   /api/v1/payments/{id}/authorize     PENDING → AUTHORIZED
POST   /api/v1/payments/{id}/capture       AUTHORIZED → CAPTURED
POST   /api/v1/payments/{id}/cancel        AUTHORIZED → CANCELLED
POST   /api/v1/payments/{id}/fail          PENDING → FAILED
GET    /api/v1/payments?walletId=          List payments for a wallet
```

---

### wallet-service — Port 8082

Implements double-entry accounting. Every money movement creates two ledger entries — a DEBIT
on one wallet and a CREDIT on another — inside a single `@Transactional`. The books always
balance because partial writes are impossible.

**Key design decisions:**

- No `balance` column on the `wallets` table — balance is always computed as `running_balance`
  of the latest `ledger_entry`. A balance column can silently drift; the ledger never lies
- `running_balance` on each entry enables O(1) balance reads via
  `SELECT running_balance FROM ledger_entries WHERE wallet_id=? ORDER BY id DESC LIMIT 1`
  with a composite index on `(wallet_id, id DESC)`
- `wallet.transactions` is the FK parent of `wallet.ledger_entries` — transaction row is
  always inserted before ledger entries, enforced by the database constraint
- All write endpoints are idempotent — the same `idempotencyKey` always returns the same
  transaction, making it safe to retry after network failures

**Endpoints:**

```
POST   /api/v1/wallets                     Create wallet
GET    /api/v1/wallets/{id}                Get wallet + current balance
GET    /api/v1/wallets/{id}/balance        Current balance (O(1) indexed read)
POST   /api/v1/wallets/{id}/credit         Add funds (idempotent)
POST   /api/v1/wallets/{id}/debit          Remove funds (idempotent, 422 if insufficient)
GET    /api/v1/wallets/{id}/history        Full ledger entry audit trail
```

---

### ledger-service — Port 8083

Event-sourced financial record. Stores every money movement as an immutable append-only event.
Supports point-in-time balance reconstruction — "what was this wallet's balance at 10:30am
on July 10?" — by replaying events up to that timestamp.

**Key design decisions:**

- `BIGSERIAL` primary key guarantees insertion order without relying on timestamps
  (two events can have the same millisecond timestamp under load)
- `occurred_at` vs `recorded_at`: `occurred_at` is the business time (when the debit
  happened), `recorded_at` is when this service wrote it. These diverge when Kafka has lag.
  Point-in-time queries use `occurred_at`
- `idempotency_key UNIQUE` prevents Kafka at-least-once delivery from creating duplicate
  ledger entries when a consumer restarts after a crash

**Endpoints:**

```
POST   /api/v1/ledger/events               Append event (idempotent)
GET    /api/v1/ledger/{walletId}/events    Get all events for a wallet
GET    /api/v1/ledger/{walletId}/balance   Current balance from events
GET    /api/v1/ledger/{walletId}/balance?at={timestamp}   Point-in-time balance
GET    /api/v1/ledger/replay/{walletId}    Replay all events from beginning
```

---

### saga-coordinator — Port 8084

Orchestrates the cross-service payment flow. Drives each step of the payment saga and triggers
compensation if any step fails. Every state transition is recorded in `saga_steps` — a
complete, immutable audit trail of what happened and in what order.

**Key design decisions:**

- Saga vs database transaction: a DB transaction only spans one database. A saga spans four
  services. When the wallet debit fails after the payment is authorized, the saga detects
  the failure and fires compensation — cancelling the payment and crediting the wallet back
- Compensation is not rollback. The payment was authorized — that happened. Compensation
  creates new events that undo the effect. Both the original and compensation events are
  recorded for audit
- `@Version` on `PaymentSaga` prevents two saga coordinator instances from advancing the
  same saga concurrently

**Saga state machine:**

```
Happy path:
STARTED → PAYMENT_INITIATED → WALLET_DEBITED → LEDGER_RECORDED → PAYMENT_CAPTURED → COMPLETED

Compensation path:
STARTED → PAYMENT_INITIATED → COMPENSATION_STARTED → PAYMENT_VOIDED → COMPENSATED
```

**Endpoints:**

```
POST   /api/v1/sagas/payment               Start payment saga
GET    /api/v1/sagas/{id}                  Get saga state
GET    /api/v1/sagas/payment/{paymentId}   Find saga by payment
GET    /api/v1/sagas/{id}/steps            Full step audit trail
POST   /api/v1/sagas/{id}/advance          Advance to next state
POST   /api/v1/sagas/{id}/compensate       Trigger compensation
```

---

## Database Schema

One PostgreSQL instance, four schemas — isolated by schema-level users with least-privilege
grants. In production each service would have its own database cluster.

```
stripe_platform database
├── payment schema  (payment_svc user)
│   ├── payments         — 12-state payment lifecycle, optimistic locking
│   └── outbox_events    — transactional outbox for Kafka publishing (Phase 2)
│
├── wallet schema   (wallet_svc user)
│   ├── wallets          — no balance column (balance = ledger running_balance)
│   ├── transactions     — FK parent for ledger_entries
│   └── ledger_entries   — append-only, double-entry records, BIGSERIAL PK
│
├── ledger schema   (ledger_svc user)
│   └── ledger_events    — event store, JSONB payload, point-in-time capable
│
└── saga schema     (saga_svc user)
    ├── sagas            — one row per payment flow, optimistic locking
    └── saga_steps       — append-only step audit trail
```

No cross-schema foreign keys. Services are isolated — they communicate only through
REST APIs and Kafka events, never through database joins.

---

## Kafka Topics

All topics are partitioned by `walletId` — all events for one wallet go to the same
partition, guaranteeing ordered processing per wallet without global ordering overhead.

| Topic | Partitions | Producer | Consumers | Retention |
|---|---|---|---|---|
| `payment-events` | 6 | payment-service | ledger-service, saga-coordinator | 7 days |
| `wallet-events` | 6 | wallet-service | ledger-service, saga-coordinator | 7 days |
| `ledger-events` | 6 | ledger-service | analytics, audit | 7 days (compacted) |
| `saga-commands` | 4 | saga-coordinator | payment-service, wallet-service | 7 days |
| `*.DLQ` | 2 each | all services | ops monitoring | 30 days |

**Event contracts** (defined in `shared/events/`):

- `PaymentInitiated` — emitted when payment is created, triggers saga start
- `FundsDebited` — emitted by wallet-service, consumed by ledger-service to write audit record
- `PaymentSettled` — terminal success event, partitioned by `merchantId` so merchants receive their own events

---

## Key Engineering Patterns

### Transactional Outbox
Payment state change and Kafka event written in one `@Transactional`. A relay job
publishes events from the outbox table. Kafka being down during a capture never
causes a missed `PaymentCaptured` event — it is safely in the database waiting.

### Idempotency at Every Layer
Every write endpoint accepts an `idempotencyKey`. The same key returns the same result
without any side effects. Enforced at the Java layer (early return) and the database
layer (UNIQUE constraint). Network retries never create duplicate charges.

### Optimistic Locking
`@Version` on `Payment` and `PaymentSaga` entities. Hibernate adds `AND version=N`
to every UPDATE. Two concurrent capture requests: first writer increments version,
second writer's WHERE clause matches nothing — throws `OptimisticLockingFailureException`
— HTTP 409 returned — client retries safely.

### Running Balance (No Balance Column)
Wallet balance is the `running_balance` of the most recent `ledger_entry`. A balance
column could drift silently if a bug skips an update. The ledger history is the
source of truth and can always be replayed to verify any balance.

### Event Sourcing
The ledger service stores events, not state. To reconstruct a wallet's balance at any
point in time: replay all events up to that timestamp. This enables historical auditing,
bug investigation, and compliance reporting without needing time-travel queries against
a mutable state table.

### Saga Orchestration
The saga coordinator owns the cross-service flow. When a step fails, the coordinator
fires compensation steps in reverse order — crediting the wallet, cancelling the payment.
Every step is recorded in `saga_steps` providing a complete audit trail of both the
happy path and compensation path.

---

## Local Development Setup

**Prerequisites:** Docker, Java 21, Maven

**1. Start infrastructure**

```bash
docker compose up -d
```

This starts: PostgreSQL 15, Apache Kafka 3.6 (KRaft mode), Redis 7,
Kafka UI (localhost:8080), Redis Commander (localhost:8085).

**2. Verify infrastructure**

```bash
docker compose ps
# All services should show "healthy"
```

**3. Start services** (each in a separate terminal)

```bash
mvn -pl services/payment-service spring-boot:run    # port 8081
mvn -pl services/wallet-service spring-boot:run     # port 8082
mvn -pl services/ledger-service spring-boot:run     # port 8083
mvn -pl services/saga-coordinator spring-boot:run   # port 8084
```

On first start, Flyway automatically creates all 8 tables across the 4 schemas.

**4. Verify all services**

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
# All return: {"status":"UP"}
```

---

## End-to-End Payment Flow

```bash
# 1. Create a wallet and fund it
curl -s -X POST http://localhost:8082/api/v1/wallets \
  -H "Content-Type: application/json" \
  -d '{"ownerId":"11111111-1111-1111-1111-111111111111","currency":"USD"}'

WALLET_ID="<id from response>"

curl -s -X POST http://localhost:8082/api/v1/wallets/$WALLET_ID/credit \
  -H "Content-Type: application/json" \
  -d '{"amountCents":10000,"currency":"USD","idempotencyKey":"topup-001"}'

# 2. Create and process a payment
curl -s -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"walletId":"'$WALLET_ID'","merchantId":"22222222-2222-2222-2222-222222222222",
       "amountCents":5000,"currency":"USD","idempotencyKey":"order-001"}'

PAYMENT_ID="<id from response>"

curl -s -X POST http://localhost:8081/api/v1/payments/$PAYMENT_ID/authorize
curl -s -X POST http://localhost:8081/api/v1/payments/$PAYMENT_ID/capture

# 3. Debit the wallet
curl -s -X POST http://localhost:8082/api/v1/wallets/$WALLET_ID/debit \
  -H "Content-Type: application/json" \
  -d '{"amountCents":5000,"currency":"USD",
       "idempotencyKey":"debit-'$PAYMENT_ID'","referenceId":"'$PAYMENT_ID'"}'

# 4. Verify balance (should be 5000)
curl -s http://localhost:8082/api/v1/wallets/$WALLET_ID/balance

# 5. Check ledger history
curl -s http://localhost:8082/api/v1/wallets/$WALLET_ID/history

# 6. Check ledger service event store
curl -s http://localhost:8083/api/v1/ledger/$WALLET_ID/events
```

---

## Project Structure

```
mini-stripe/
├── docker-compose.yml                    Local dev infrastructure
├── infra/
│   ├── postgres/init.sql                 Schema + user creation
│   ├── kafka/init-topics.sh              Topic creation with production settings
│   └── k8s/                             Kubernetes deployment manifests
├── shared/
│   └── events/                          Kafka event schemas (JSON contracts)
│       ├── PaymentInitiated.json
│       ├── FundsDebited.json
│       └── PaymentSettled.json
├── docs/
│   └── ADRs/
│       └── ADR-001-stack-choices.md      Architecture Decision Records
├── observability/
│   └── prometheus/prometheus.yml         Metrics scrape config
└── services/
    ├── payment-service/                  Port 8081
    │   └── src/main/java/.../payment/
    │       ├── domain/                   Payment aggregate, PaymentStatus FSM
    │       ├── repository/               Spring Data JPA repositories
    │       ├── service/                  Business logic, idempotency
    │       └── api/                      REST controllers, DTOs, exception handlers
    ├── wallet-service/                   Port 8082
    │   └── src/main/java/.../wallet/
    │       ├── domain/                   Wallet, Transaction, LedgerEntry
    │       ├── repository/
    │       ├── service/                  debitWallet, creditWallet, getBalance
    │       └── api/
    ├── ledger-service/                   Port 8083
    │   └── src/main/java/.../ledger/
    │       ├── domain/                   LedgerEvent
    │       ├── repository/
    │       ├── service/                  appendEvent, replayFromBeginning, getBalanceAt
    │       └── api/
    └── saga-coordinator/                 Port 8084
        └── src/main/java/.../saga/
            ├── domain/                   PaymentSaga, SagaStatus FSM, SagaStep
            ├── repository/
            ├── service/                  Saga orchestration + compensation
            └── api/
```

---

## Development Roadmap

### Phase 1 — Four Services (Complete)
All four services boot independently. REST APIs work. Flyway migrations run. All 8 tables
exist. Payment state machine, double-entry wallet, event-sourced ledger, and saga orchestration
all implemented with full idempotency and optimistic locking.

### Phase 2 — Kafka Event-Driven Architecture (In Progress)
Transactional outbox relay in payment-service. `@KafkaListener` consumers in wallet-service
and ledger-service. `PaymentCaptured` triggers automatic wallet debit. `FundsDebited` triggers
automatic ledger record. Consumer group offset management with `enable-auto-commit: false`.
Dead letter queue handling for unprocessable messages.

### Phase 3 — Saga Automation + Redis
Saga coordinator driven entirely by Kafka events — no manual API calls. Compensation fires
automatically when any consumer throws. Redis distributed locks (Redlock) before wallet debit
to prevent concurrent double-debit. Sliding window rate limiting per wallet. Balance caching
with cache-aside pattern and TTL-based invalidation.

### Phase 4 — Observability + Kubernetes
Prometheus metrics (payment success rate, P99 latency, wallet debit throughput). Grafana
dashboards. OpenTelemetry distributed tracing across all four services. Kubernetes deployment
with HPA, PodDisruptionBudgets, and rolling updates. Chaos engineering: kill services mid-saga
and verify compensation completes correctly.

### Phase 5 — Settlement + Advanced Features
Nightly settlement batch: aggregate daily transactions and move funds to merchant wallets.
`PaymentSettled` event partitioned by `merchantId`. Dispute management flow. Fraud detection
hooks via Kafka consumer. CDC (Change Data Capture) via PostgreSQL logical replication.

---

## Architecture Decision Records

| ADR | Decision | Status |
|---|---|---|
| ADR-001 | Java 21 + Spring Boot, PostgreSQL, Kafka, Redis, Kubernetes | Accepted |
| ADR-002 | Saga orchestration over choreography for payment flow | In Progress |

---

## Observability

All services expose:
- `/actuator/health` — liveness and readiness probes (used by Kubernetes)
- `/actuator/prometheus` — Prometheus metrics endpoint (scraped every 15s)

Kafka UI at `localhost:8080` — browse topics, consumer groups, message offsets.

Redis Commander at `localhost:8085` — browse cache keys, lock state.

---

## What I Learned Building This

**Distributed systems fundamentals** — why two-phase commit does not scale and why the saga
pattern exists. What compensation means versus rollback. How partial failures create
inconsistent state and how to design systems that survive them.

**PostgreSQL internals** — MVCC and how xmin/xmax enable readers and writers to run without
blocking each other. B-tree index internals: page splits, branching factor, why 10 billion
rows still need only 4 page reads. VACUUM and dead tuple accumulation. Why BIGSERIAL and UUID
primary keys behave differently in clustered vs heap storage.

**Kafka internals** — why partition ordering guarantees consistency per wallet without global
ordering. The difference between at-least-once and exactly-once delivery. Why consumer group
offset management matters for financial systems. The outbox pattern as the bridge between
database atomicity and message broker delivery.

**Java + Spring Boot** — what `@Transactional` does at the PostgreSQL level (BEGIN/COMMIT/ROLLBACK).
How Hibernate dirty checking works. Why `@Version` generates `AND version=N` in UPDATE
statements. HikariCP connection pool sizing and why it matters under load.

**Financial system design** — why amounts are always stored as integer cents (never float).
Double-entry accounting and why the ledger never has a balance column. Idempotency as a
first-class requirement, not an afterthought. The difference between occurred_at and
recorded_at in event-sourced systems.
