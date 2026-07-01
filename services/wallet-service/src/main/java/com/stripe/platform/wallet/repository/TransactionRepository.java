// services/wallet-service/src/main/java/com/stripe/platform/wallet/repository/TransactionRepository.java
package com.stripe.platform.wallet.repository;

import com.stripe.platform.wallet.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    // For idempotency check — same key returns same transaction
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}