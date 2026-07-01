// services/wallet-service/src/main/java/com/stripe/platform/wallet/repository/WalletRepository.java
package com.stripe.platform.wallet.repository;

import com.stripe.platform.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    List<Wallet> findByOwnerId(UUID ownerId);
}