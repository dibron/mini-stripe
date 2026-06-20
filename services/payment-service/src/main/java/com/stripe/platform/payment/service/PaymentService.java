package com.stripe.platform.payment.service;

import com.stripe.platform.payment.domain.Payment;
import com.stripe.platform.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment application service.
 *
 * IDEMPOTENCY PATTERN:
 * Every write operation checks for an existing record with the same idempotency_key.
 * If found, return the existing result. This makes all endpoints safe to retry.
 * The UNIQUE constraint on idempotency_key is the database-level safety net.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * Initiate a new payment — idempotent.
     * If a payment with this idempotencyKey already exists, return it unchanged.
     */
    @Transactional
    public Payment initiatePayment(UUID walletId, UUID merchantId,
                                   Long amountCents, String currency,
                                   String idempotencyKey) {
        // Idempotency check: have we seen this key before?
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Returning existing payment for idempotency key: {}", idempotencyKey);
            return existing.get();
        }

        Payment payment = Payment.initiate(walletId, merchantId, amountCents,
                                           currency, idempotencyKey);
        payment.submitToPending();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment initiated: id={} amount={}{}",
                 saved.getId(), saved.getAmountCents(), saved.getCurrency());

        // Phase 2: publish PaymentInitiated event to outbox here
        return saved;
    }

    @Transactional
    public Payment authorizePayment(UUID paymentId) {
        Payment payment = findOrThrow(paymentId);
        payment.authorize();
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment capturePayment(UUID paymentId) {
        Payment payment = findOrThrow(paymentId);
        payment.capture();
        log.info("Payment captured: id={}", paymentId);
        // Phase 2: publish PaymentCaptured event to outbox here
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment failPayment(UUID paymentId, String reason) {
        Payment payment = findOrThrow(paymentId);
        payment.fail(reason);
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(UUID paymentId) {
        return findOrThrow(paymentId);
    }

    private Payment findOrThrow(UUID paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
