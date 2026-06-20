# Kafka Event Schemas

All Kafka event schemas are defined here as the contract between producers and consumers.

## Rules
1. Never add a REQUIRED field to an existing schema without a migration plan
2. Never remove or rename a field without bumping the version
3. Every event MUST have: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `idempotencyKey`
4. `amountCents` is always BIGINT — never float or decimal
5. The `eventId` is used for consumer-side deduplication

## Topics
| Topic | Producer | Consumers |
|---|---|---|
| payment-events | payment-service | ledger-service, saga-coordinator, notifications |
| wallet-events | wallet-service | ledger-service, saga-coordinator |
| ledger-events | ledger-service | analytics, audit |
| saga-commands | saga-coordinator | payment-service, wallet-service |

## Schemas
- [PaymentInitiated.json](PaymentInitiated.json)
- [FundsDebited.json](FundsDebited.json)
- [PaymentSettled.json](PaymentSettled.json)
