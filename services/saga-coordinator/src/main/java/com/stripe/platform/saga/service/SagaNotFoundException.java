package com.stripe.platform.saga.service;

import java.util.UUID;

public class SagaNotFoundException extends RuntimeException {
    public SagaNotFoundException(UUID id) {
        super("Saga not found: " + id);
    }
}