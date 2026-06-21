package com.stripe.platform.ledger.service;

import com.stripe.platform.ledger.domain.LedgerEvent;
import com.stripe.platform.ledger.repository.LedgerEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerEventRepository repository;

    /**
     * APPEND EVENT — "Write a new line in the passbook"
     *
     * Try to insert. If idempotency_key already exists (duplicate from Kafka),
     * the UNIQUE constraint throws DataIntegrityViolationException.
     * We catch it and return the original — no double counting.
     */
    @Transactional
    public LedgerEvent appendEvent(UUID aggregateId, String eventType, String eventVersion,
                                   String payload, String idempotencyKey, Instant occurredAt) {
        // Check if we already processed this event
        Optional<LedgerEvent> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate event ignored: idempotencyKey={}", idempotencyKey);
            return existing.get();
        }

        LedgerEvent event = LedgerEvent.of(aggregateId, eventType, eventVersion,
                payload, idempotencyKey, occurredAt);
        LedgerEvent saved = repository.save(event);
        log.info("Appended event: type={} aggregateId={} id={}",
                eventType, aggregateId, saved.getId());
        return saved;
    }

    /**
     * REPLAY FROM BEGINNING — "Read the passbook from page 1"
     *
     * Load ALL events in order, apply each one to calculate every wallet's balance.
     * Returns a map: walletId → balance in cents.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> replayFromBeginning() {
        List<LedgerEvent> allEvents = repository.findAll();
        allEvents.sort((a, b) -> Long.compare(a.getId(), b.getId()));

        Map<UUID, Long> balances = new HashMap<>();
        for (LedgerEvent event : allEvents) {
            UUID walletId = event.getAggregateId();
            long current = balances.getOrDefault(walletId, 0L);
            balances.put(walletId, applyEvent(current, event));
        }

        log.info("Replayed {} events across {} wallets", allEvents.size(), balances.size());
        return balances;
    }

    /**
     * GET BALANCE AT — "What was the balance on Jan 5?"
     *
     * Only looks at events where occurred_at <= the given timestamp.
     * Replays just those to compute the historical balance.
     */
    @Transactional(readOnly = true)
    public long getBalanceAt(UUID walletId, Instant at) {
        List<LedgerEvent> events =
                repository.findByAggregateIdAndOccurredAtLessThanEqualOrderByIdAsc(walletId, at);

        long balance = 0;
        for (LedgerEvent event : events) {
            balance = applyEvent(balance, event);
        }
        return balance;
    }

    /**
     * GET EVENTS — "Show me every line in Ravi's passbook"
     */
    @Transactional(readOnly = true)
    public List<LedgerEvent> getEvents(UUID walletId) {
        return repository.findByAggregateIdOrderByIdAsc(walletId);
    }

    /**
     * Apply one event to a running balance.
     *
     * FundsDebited  → subtract (money left the wallet)
     * FundsCredited → add (money entered the wallet)
     * Anything else → ignore (unknown event type, balance unchanged)
     */
    private long applyEvent(long currentBalance, LedgerEvent event) {
        long amount = extractAmountCents(event.getPayload());
        return switch (event.getEventType()) {
            case "FundsDebited" -> currentBalance - amount;
            case "FundsCredited" -> currentBalance + amount;
            default -> currentBalance;
        };
    }

    /**
     * Pull amountCents out of the JSON payload.
     * Simple parsing — no external library needed for this.
     */
    private long extractAmountCents(String payload) {
        int idx = payload.indexOf("\"amountCents\"");
        if (idx == -1) return 0;
        String after = payload.substring(idx + "\"amountCents\"".length());
        after = after.replaceFirst("^\\s*:\\s*", "");
        StringBuilder num = new StringBuilder();
        for (char c : after.toCharArray()) {
            if (Character.isDigit(c) || c == '-') num.append(c);
            else if (!num.isEmpty()) break;
        }
        return num.isEmpty() ? 0 : Long.parseLong(num.toString());
    }
}
