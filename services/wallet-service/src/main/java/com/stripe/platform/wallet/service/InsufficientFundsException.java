package com.stripe.platform.wallet.service;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(UUID walletId, Long required, Long available) {
        super(String.format(
                "Insufficient funds in wallet %s: required=%d cents, available=%d cents",
                walletId, required, available
        ));
    }
}