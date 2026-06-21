package com.stripe.platform.ledger.api;

import com.stripe.platform.ledger.domain.LedgerEvent;
import com.stripe.platform.ledger.service.LedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    /**
     * GET /api/v1/ledger/{walletId}/events
     * "Show me every line in this wallet's passbook"
     */
    @GetMapping("/{walletId}/events")
    public ResponseEntity<List<EventResponse>> getEvents(@PathVariable UUID walletId) {
        List<EventResponse> events = ledgerService.getEvents(walletId).stream()
                .map(EventResponse::from)
                .toList();
        return ResponseEntity.ok(events);
    }

    /**
     * GET /api/v1/ledger/{walletId}/balance?at=2026-06-20T10:30:00Z
     * "What was this wallet's balance at this point in time?"
     */
    @GetMapping("/{walletId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable UUID walletId,
            @RequestParam("at") Instant at) {
        long balance = ledgerService.getBalanceAt(walletId, at);
        return ResponseEntity.ok(new BalanceResponse(walletId, balance, at));
    }

    /**
     * POST /api/v1/ledger/events
     * "Write a new line in the passbook"
     * This is the manual entry point. In Phase 2, a Kafka consumer calls
     * ledgerService.appendEvent() instead — same logic, different trigger.
     */
    @PostMapping("/events")
    public ResponseEntity<EventResponse> appendEvent(@Valid @RequestBody AppendEventRequest request) {
        LedgerEvent event = ledgerService.appendEvent(
                request.aggregateId(), request.eventType(), request.eventVersion(),
                request.payload(), request.idempotencyKey(), request.occurredAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(event));
    }

    /**
     * GET /api/v1/ledger/replay
     * "Read the entire passbook from page 1, calculate all balances"
     */
    @GetMapping("/replay")
    public ResponseEntity<Map<UUID, Long>> replay() {
        return ResponseEntity.ok(ledgerService.replayFromBeginning());
    }

    // --- DTOs ---

    public record AppendEventRequest(
            @NotNull UUID aggregateId,
            @NotBlank String eventType,
            @NotBlank String eventVersion,
            @NotBlank String payload,
            @NotBlank String idempotencyKey,
            @NotNull Instant occurredAt
    ) {}

    public record EventResponse(
            Long id, UUID aggregateId, String eventType, String eventVersion,
            String payload, String idempotencyKey, String occurredAt, String recordedAt
    ) {
        public static EventResponse from(LedgerEvent e) {
            return new EventResponse(
                    e.getId(), e.getAggregateId(), e.getEventType(), e.getEventVersion(),
                    e.getPayload(), e.getIdempotencyKey(),
                    e.getOccurredAt().toString(), e.getRecordedAt().toString());
        }
    }

    public record BalanceResponse(UUID walletId, long balanceCents, Instant asOf) {}
}
