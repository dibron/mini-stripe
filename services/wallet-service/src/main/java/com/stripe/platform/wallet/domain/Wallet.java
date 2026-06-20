package com.stripe.platform.wallet.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "wallets", schema = "wallet")
@Getter @NoArgsConstructor
public class Wallet {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static Wallet create(UUID ownerId, String currency) {
        Wallet w = new Wallet();
        w.id = UUID.randomUUID();
        w.ownerId = ownerId;
        w.currency = currency;
        w.status = "ACTIVE";
        w.createdAt = Instant.now();
        return w;
    }
}
