# ADR-001: Core Technology Stack Selection

**Status:** Accepted
**Date:** 2025-06-20
**Authors:** Engineer A, Engineer B

---

## Context

We are building a distributed payment platform for learning production-grade distributed systems.
The stack must be used by real payment companies, force us to confront real distributed problems,
and be learnable incrementally.

---

## Decisions

### Java 21 + Spring Boot 3.2

Java dominates payment companies (Stripe, Brex, Adyen) for JVM memory safety and mature
concurrency primitives. Spring Boot provides: transaction management (critical for outbox
pattern), Spring Kafka with consumer group management, Micrometer for Prometheus metrics,
and Actuator health endpoints for Kubernetes probes.

**Rejected:** Go (weaker Spring Kafka integration), Python (GIL limits concurrency).

### PostgreSQL 15

Non-negotiable for payments: ACID transactions + relational integrity for double-entry.
Key features we use: MVCC (readers don't block writers), `SELECT FOR UPDATE SKIP LOCKED`
(outbox relay pattern), `SERIALIZABLE` isolation (most critical operations),
`TIMESTAMPTZ` (settlement calculations), logical replication (CDC in later phases).

**Rejected:** MongoDB (no multi-document ACID), Cassandra (trades consistency for
availability — inverse of what payments need).

### Apache Kafka 3.6

Three properties no other broker gives us:
1. **Replay** — fix a ledger bug, replay events from offset 0. RabbitMQ deletes on ack.
2. **Consumer independence** — new consumers (fraud, analytics) need zero producer changes.
3. **Partition ordering** — all events for a wallet_id hash to one partition = ordered processing.

**Rejected:** RabbitMQ (no replay), SQS (AWS lock-in, no replay), Redis Streams (weaker durability).

### Redis 7

Distributed locks (Redlock algorithm for idempotency), rate limiting (sliding window),
saga state caching, and read-model projection cache.

### Kubernetes

Production-grade deployment with HPA, PodDisruptionBudgets, rolling updates, and
health probes. Forces us to think about stateless service design.

---

## Consequences

- We learn JVM GC impact on latency under load
- We learn PostgreSQL MVCC and locking deeply (required for outbox pattern)
- We learn Kafka consumer group semantics (required for exactly-once processing)
- Local dev requires Docker (Kafka + PostgreSQL + Redis)
- Spring Boot abstractions occasionally hide internals — we deliberately go below them
