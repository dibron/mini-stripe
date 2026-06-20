package com.stripe.platform.wallet.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable double-entry ledger record.
 * NEVER updated after insert. NEVER deleted.
 * Every money movement creates exactly TWO of these per transaction.
 */
@Entity @Table(name = "ledger_entries", schema = "wallet")
@Getter @NoArgsConstructor
public class LedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false) private UUID transactionId;
    @Column(name = "wallet_id", nullable = false)      private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6) private EntryType entryType;

    /** Always positive. Direction determined by entryType. */
    @Column(nullable = false) private Long amount;

    /** Running balance AFTER this entry. Denormalized for fast reads. */
    @Column(name = "running_balance", nullable = false) private Long runningBalance;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static LedgerEntry of(UUID transactionId, UUID walletId,
                                 EntryType type, Long amount, Long runningBalance) {
        LedgerEntry e = new LedgerEntry();
        e.transactionId = transactionId;
        e.walletId = walletId;
        e.entryType = type;
        e.amount = amount;
        e.runningBalance = runningBalance;
        e.createdAt = Instant.now();
        return e;
    }
}
