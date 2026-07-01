// services/wallet-service/src/main/java/com/stripe/platform/wallet/repository/LedgerEntryRepository.java
package com.stripe.platform.wallet.repository;

import com.stripe.platform.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Current balance = running_balance of the LATEST entry for this wallet.
     * "Latest" = highest id (BIGSERIAL, auto-incremented, always ascending).
     *
     * Generated SQL:
     * SELECT * FROM wallet.ledger_entries
     * WHERE wallet_id = ?
     * ORDER BY id DESC
     * LIMIT 1
     */
    Optional<LedgerEntry> findTopByWalletIdOrderByIdDesc(UUID walletId);

    /**
     * Get all entries for a wallet — for audit and history endpoints.
     */
    List<LedgerEntry> findByWalletIdOrderByIdAsc(UUID walletId);
}