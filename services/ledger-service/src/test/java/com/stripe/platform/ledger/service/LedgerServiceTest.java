package com.stripe.platform.ledger.service;

import com.stripe.platform.ledger.domain.LedgerEvent;
import com.stripe.platform.ledger.repository.LedgerEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test — uses the local Docker PostgreSQL (docker-compose).
 * Make sure docker-compose is running before executing these tests.
 */
@SpringBootTest
class LedgerServiceTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerEventRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    /**
     * Definition of done #1:
     * Append 3 FundsDebited events for the same wallet, replay produces correct balance
     */
    @Test
    void append3FundsDebited_replayProducesCorrectBalance() {
        UUID walletId = UUID.randomUUID();
        Instant now = Instant.now();

        appendDebit(walletId, 15000, "rent-" + walletId, now);
        appendDebit(walletId, 3000, "grocery-" + walletId, now);
        appendDebit(walletId, 500, "phone-" + walletId, now);

        Map<UUID, Long> balances = ledgerService.replayFromBeginning();

        assertThat(balances).containsEntry(walletId, -18500L);
    }

    /**
     * Definition of done #2:
     * Appending same event twice (same idempotency_key) succeeds idempotently
     */
    @Test
    void duplicateIdempotencyKey_returnsOriginal_noDuplicateInDb() {
        UUID walletId = UUID.randomUUID();
        Instant now = Instant.now();
        String key = "dup-test-" + walletId;

        LedgerEvent first = appendDebit(walletId, 5000, key, now);
        LedgerEvent second = appendDebit(walletId, 5000, key, now);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(repository.count()).isEqualTo(1);

        Map<UUID, Long> balances = ledgerService.replayFromBeginning();
        assertThat(balances).containsEntry(walletId, -5000L);
    }

    /**
     * Definition of done #3:
     * getBalanceAt(walletId, yesterday) returns correct historical balance
     */
    @Test
    void getBalanceAt_returnsHistoricalBalance() {
        UUID walletId = UUID.randomUUID();
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant today = Instant.now();

        ledgerService.appendEvent(walletId, "FundsCredited", "1.0",
                "{\"amountCents\": 50000}", "salary-" + walletId, twoDaysAgo);

        ledgerService.appendEvent(walletId, "FundsDebited", "1.0",
                "{\"amountCents\": 15000}", "rent-" + walletId, yesterday);

        ledgerService.appendEvent(walletId, "FundsDebited", "1.0",
                "{\"amountCents\": 3000}", "grocery-" + walletId, today);

        assertThat(ledgerService.getBalanceAt(walletId, twoDaysAgo)).isEqualTo(50000L);
        assertThat(ledgerService.getBalanceAt(walletId, yesterday)).isEqualTo(35000L);
        assertThat(ledgerService.getBalanceAt(walletId, today)).isEqualTo(32000L);
    }

    /**
     * Append 100 events, replay, verify final state matches
     */
    @Test
    void append100Events_replayMatchesExpectedBalance() {
        UUID walletId = UUID.randomUUID();
        Instant now = Instant.now();

        for (int i = 0; i < 100; i++) {
            appendDebit(walletId, 1000, "evt-" + walletId + "-" + i, now.plusSeconds(i));
        }

        assertThat(repository.count()).isEqualTo(100);

        Map<UUID, Long> balances = ledgerService.replayFromBeginning();
        assertThat(balances).containsEntry(walletId, -100_000L);
    }

    private LedgerEvent appendDebit(UUID walletId, long amountCents,
                                     String idempotencyKey, Instant occurredAt) {
        String payload = String.format("{\"amountCents\": %d, \"currency\": \"USD\"}", amountCents);
        return ledgerService.appendEvent(walletId, "FundsDebited", "1.0",
                payload, idempotencyKey, occurredAt);
    }
}
