package com.stripe.platform.wallet.service;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(UUID walletId) {
        super("Wallet not found: " + walletId);
    }
}