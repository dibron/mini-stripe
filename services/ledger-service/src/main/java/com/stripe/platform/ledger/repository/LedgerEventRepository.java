package com.stripe.platform.ledger.repository;

import com.stripe.platform.ledger.domain.LedgerEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEventRepository extends JpaRepository<LedgerEvent, Long> {

    // "find the event with this idempotency key" — for duplicate detection
    Optional<LedgerEvent> findByIdempotencyKey(String idempotencyKey);

    // "get all events for this wallet, oldest first" — for replay
    List<LedgerEvent> findByAggregateIdOrderByIdAsc(UUID aggregateId);

    // "get events for this wallet that happened on or before this time" — for point-in-time balance
    List<LedgerEvent> findByAggregateIdAndOccurredAtLessThanEqualOrderByIdAsc(
            UUID aggregateId, Instant cutoff);
}
