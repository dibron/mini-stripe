# Phase 1 GitHub Issues

Create these 4 issues in your GitHub repo before starting Phase 1.

---

## Issue #1 — Engineer A — Week 1

**Title:** `[payment-service] Scaffold Spring Boot with payment aggregate and state machine`
**Labels:** `engineer-a` `phase-1` `week-1`

### Goal
Build payment-service with the complete payment aggregate and state machine.

### Tasks
- [ ] Spring Boot 3 project with pom.xml dependencies
- [ ] `Payment` aggregate — all states from state machine
- [ ] `PaymentStatus` enum with `transitionTo()` enforcement
- [ ] PostgreSQL migration: `payment.payments` table
- [ ] `PaymentRepository` (Spring Data JPA)
- [ ] `PaymentService` with `initiatePayment()`, `capturePayment()`
- [ ] `PaymentController` — POST /api/v1/payments, GET /api/v1/payments/{id}
- [ ] Unit tests for ALL state transitions (legal + illegal)

### Learning objectives BEFORE coding
1. Why does the aggregate enforce transitions instead of the service layer?
2. What is optimistic locking and why does `version` prevent concurrent updates?
3. What does PostgreSQL MVCC do when two requests update the same payment row?

### Definition of done
- All illegal transitions throw `IllegalPaymentTransitionException`
- `docker-compose up` → service starts on :8081
- Engineer B has read and understood the state machine

---

## Issue #2 — Engineer A — Week 2

**Title:** `[wallet-service] Scaffold Spring Boot with double-entry ledger schema`
**Labels:** `engineer-a` `phase-1` `week-2`

### Goal
Build wallet-service with the complete double-entry accounting schema.

### Tasks
- [ ] Spring Boot 3 project scaffold
- [ ] PostgreSQL migration: `wallet.wallets`, `wallet.transactions`, `wallet.ledger_entries`
- [ ] `WalletService.debitWallet()` — creates BOTH entries in ONE `@Transactional`
- [ ] `WalletService.getBalance()` — reads `running_balance` from latest entry
- [ ] REST: POST /api/v1/wallets, GET /api/v1/wallets/{id}/balance, POST /api/v1/wallets/{id}/debit
- [ ] Integration test: debit and verify `running_balance` correct
- [ ] Concurrency test: 10 threads debit simultaneously — no incorrect balance

### Learning objectives BEFORE coding
1. Why is `amount` stored as `BIGINT` cents, not `DECIMAL`?
2. What does `@Transactional` do at the PostgreSQL level?
3. Why does `wallets` have no `balance` column?

### Blocks
Issue #4 — Engineer B needs this schema to design their ledger consumer.

---

## Issue #3 — Engineer B — Week 1

**Title:** `[ledger-service] Scaffold Spring Boot with event-sourced financial record`
**Labels:** `engineer-b` `phase-1` `week-1`

### Tasks
- [ ] Spring Boot 3 project scaffold
- [ ] PostgreSQL migration: `ledger.ledger_events` table
- [ ] `LedgerService.appendEvent()` — inserts, NEVER updates
- [ ] `LedgerService.replayFromBeginning()` — reconstructs state from all events
- [ ] `LedgerService.getBalanceAt(walletId, timestamp)` — point-in-time balance
- [ ] REST: GET /api/v1/ledger/{walletId}/events, GET /api/v1/ledger/{walletId}/balance?at=
- [ ] Test: append 100 events → replay → verify final state

### Learning objectives BEFORE coding
1. Why is `id` a `BIGSERIAL` (not UUID)?
2. What is the difference between `occurred_at` and `recorded_at`?
3. How do you replay 1M events without loading them all into memory?
4. What does `idempotency_key UNIQUE` prevent when Kafka delivers an event twice?

---

## Issue #4 — Engineer B — Week 2

**Title:** `[saga-coordinator] Design and scaffold the payment saga state machine`
**Labels:** `engineer-b` `phase-1` `week-2`

### Tasks
- [ ] Spring Boot 3 project scaffold
- [ ] PostgreSQL migration: `saga.sagas`, `saga.saga_steps`
- [ ] `PaymentSaga` domain with `SagaStatus` state machine
- [ ] `SagaCoordinator` service — decides next step
- [ ] Document: complete saga step sequence + compensation for each step
- [ ] REST: POST /api/v1/sagas/payment, GET /api/v1/sagas/{id}
- [ ] Write ADR-002: Saga orchestration vs choreography

### Saga steps to design
```
STARTED → PAYMENT_INITIATED → WALLET_DEBITED → LEDGER_RECORDED
→ PAYMENT_CAPTURED → COMPLETED

Compensation: COMPENSATION_STARTED → WALLET_CREDITED → PAYMENT_VOIDED → COMPENSATED
```

### Learning objectives BEFORE coding
1. What is the difference between a saga and a database transaction?
2. Why is compensation NOT the same as rollback?
3. What happens if the saga coordinator itself crashes mid-saga?

### Blocked by
Issue #1 — must understand payment-service state machine before designing the saga.
