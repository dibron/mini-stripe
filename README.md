# Stripe Learning Platform

A production-grade distributed payment system built as an engineering apprenticeship.
The goal is deep understanding of distributed systems, event-driven architecture,
and financial domain complexity — not just making things work.

---

## Engineers

| Session | Owns | Services |
|---|---|---|
| **Session A** | Payment + Wallet | `payment-service`, `wallet-service` |
| **Session B** | Ledger + Saga | `ledger-service`, `saga-coordinator` |
| **Both** | Shared contracts | `shared/`, `docs/ADRs/`, `docker-compose.yml` |

---

## Services

| Service | Owner | Port | Responsibility |
|---|---|---|---|
| payment-service | Session A | 8081 | Auth, capture, refund state machine |
| wallet-service | Session A | 8082 | Double-entry accounting, balances |
| ledger-service | Session B | 8083 | Immutable event-sourced financial record |
| saga-coordinator | Session B | 8084 | Orchestrate cross-service transactions |

---

## Tech Stack

- **Language:** Java 21
- **Framework:** Spring Boot 3.2
- **Database:** PostgreSQL 15 (per-service schema isolation)
- **Messaging:** Apache Kafka 3.6
- **Cache / Locks:** Redis 7
- **Container:** Docker + Kubernetes
- **RPC:** gRPC (inter-service)
- **Observability:** Prometheus + Grafana + OpenTelemetry
- **Migrations:** Flyway

---

## Local Dev Setup

```bash
# Start all infrastructure
docker-compose up -d

# Verify all services healthy
docker-compose ps

# Access Kafka UI
open http://localhost:8080

# Access services
curl http://localhost:8081/actuator/health   # payment-service
curl http://localhost:8082/actuator/health   # wallet-service
curl http://localhost:8083/actuator/health   # ledger-service
curl http://localhost:8084/actuator/health   # saga-coordinator
```

---

## Phases

| Phase | Weeks | Theme |
|---|---|---|
| Phase 0 | — | Setup, contracts, event schemas, ADRs |
| Phase 1 | 1–2 | First services: domain modeling, state machines, schemas |
| Phase 2 | 3–4 | Event-driven: outbox pattern, Kafka producers and consumers |
| Phase 3 | 5–6 | Consistency: saga pattern, CQRS, event sourcing |
| Phase 4 | 7–8 | Production: Redis, Kubernetes, observability |
| Phase 5 | 9–10 | Resilience: chaos engineering, fault tolerance, settlement |

---

## Repository Structure

```
stripe-learning-platform/
├── services/               # Business services (owned per engineer)
│   ├── payment-service/    # Session A
│   ├── wallet-service/     # Session A
│   ├── ledger-service/     # Session B
│   └── saga-coordinator/   # Session B
├── shared/                 # Contracts owned by both
│   ├── events/             # Kafka event schemas
│   ├── proto/              # gRPC contracts
│   └── models/             # Shared Java domain library
├── infra/                  # Infrastructure configs
│   ├── kafka/
│   ├── redis/
│   └── k8s/
├── observability/          # Monitoring stack
├── docs/
│   ├── ADRs/               # Architecture Decision Records
│   └── runbooks/
└── docker-compose.yml      # Full local stack
```

---

## Key Engineering Principles

1. **Money is never lost** — every cent is traceable through the double-entry ledger
2. **Every operation is idempotent** — safe to retry at any layer
3. **Every saga is compensatable** — no stuck states without a defined exit
4. **Every failure is observable** — metrics, traces, and alerts before the user notices
5. **Every decision is an ADR** — we document why, not just what

---

## ADR Index

| ADR | Title | Status |
|---|---|---|
| [ADR-001](docs/ADRs/ADR-001-stack-choices.md) | Core technology stack selection | Accepted |

