#!/bin/bash
# =============================================================================
# Kafka Topic Initialization — Stripe Learning Platform
# Creates all required topics with production-appropriate settings.
# Run once after Kafka starts.
# =============================================================================

BOOTSTRAP="kafka:9092"
REPLICATION=1       # Local dev: 1. Production: 3
WAIT_SECONDS=30

echo "Waiting ${WAIT_SECONDS}s for Kafka to be ready..."
sleep $WAIT_SECONDS

create_topic() {
  local topic=$1
  local partitions=$2
  local retention_ms=$3
  local extra_config=$4

  echo "Creating topic: $topic (partitions=$partitions)"
  kafka-topics --bootstrap-server $BOOTSTRAP \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor $REPLICATION \
    --config retention.ms="$retention_ms" \
    --config min.insync.replicas=1 \
    $extra_config
}

# ---------------------------------------------------------------------------
# payment-events: partitioned by wallet_id for ordering per wallet
# Retention: 7 days (604800000 ms)
# ---------------------------------------------------------------------------
create_topic "payment-events"          6  604800000

# ---------------------------------------------------------------------------
# wallet-events: partitioned by wallet_id
# ---------------------------------------------------------------------------
create_topic "wallet-events"           6  604800000

# ---------------------------------------------------------------------------
# ledger-events: partitioned by aggregate_id (wallet_id)
# Compacted: we keep the latest state per key (for snapshots)
# ---------------------------------------------------------------------------
create_topic "ledger-events"           6  604800000 "--config cleanup.policy=compact"

# ---------------------------------------------------------------------------
# saga-commands: partitioned by saga_id
# ---------------------------------------------------------------------------
create_topic "saga-commands"           4  604800000

# ---------------------------------------------------------------------------
# Dead Letter Queues — for failed/unprocessable messages
# Retention: 30 days for investigation
# ---------------------------------------------------------------------------
create_topic "payment-events.DLQ"      2  2592000000
create_topic "wallet-events.DLQ"       2  2592000000
create_topic "ledger-events.DLQ"       2  2592000000
create_topic "saga-commands.DLQ"       2  2592000000

echo "All topics created successfully."
kafka-topics --bootstrap-server $BOOTSTRAP --list
