package com.stripe.platform.saga.service;

import com.stripe.platform.saga.domain.PaymentSaga;
import com.stripe.platform.saga.domain.SagaStatus;
import com.stripe.platform.saga.domain.SagaStep;
import com.stripe.platform.saga.repository.SagaRepository;
import com.stripe.platform.saga.repository.SagaStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class SagaService {

    private final SagaRepository     sagaRepository;
    private final SagaStepRepository sagaStepRepository;



    @Transactional
    public PaymentSaga startSaga(UUID paymentId, UUID walletId,
                                 Long amountCents, String currency) {
        // Check if a saga already exists for this payment (idempotency)
        return sagaRepository.findByPaymentId(paymentId).orElseGet(() -> {
            PaymentSaga saga = PaymentSaga.start(
                    paymentId, walletId, amountCents, currency);
            PaymentSaga saved = sagaRepository.save(saga);

            // Record the first step
            recordStep(saved.getId(), "SAGA_STARTED", "COMPLETED",
                    String.format("{\"paymentId\":\"%s\",\"amountCents\":%d}",
                            paymentId, amountCents));

            log.info("Saga started: id={} paymentId={}", saved.getId(), paymentId);
            return saved;
        });
    }



    @Transactional
    public PaymentSaga paymentInitiated(UUID sagaId) {
        return advance(sagaId, SagaStatus.PAYMENT_INITIATED, "PAYMENT_INITIATED",
                "Payment created and submitted to processor");
    }

    @Transactional
    public PaymentSaga walletDebited(UUID sagaId) {
        return advance(sagaId, SagaStatus.WALLET_DEBITED, "WALLET_DEBITED",
                "Customer wallet debited successfully");
    }

    @Transactional
    public PaymentSaga ledgerRecorded(UUID sagaId) {
        return advance(sagaId, SagaStatus.LEDGER_RECORDED, "LEDGER_RECORDED",
                "Ledger event recorded");
    }

    @Transactional
    public PaymentSaga paymentCaptured(UUID sagaId) {
        return advance(sagaId, SagaStatus.PAYMENT_CAPTURED, "PAYMENT_CAPTURED",
                "Payment captured");
    }

    @Transactional
    public PaymentSaga complete(UUID sagaId) {
        return advance(sagaId, SagaStatus.COMPLETED, "SAGA_COMPLETED",
                "All steps completed successfully");
    }



    @Transactional
    public PaymentSaga startCompensation(UUID sagaId, String reason) {
        PaymentSaga saga = findOrThrow(sagaId);
        saga.advance(SagaStatus.COMPENSATION_STARTED);
        PaymentSaga saved = sagaRepository.save(saga);

        recordStep(sagaId, "COMPENSATION_STARTED", "COMPLETED",
                String.format("{\"reason\":\"%s\"}", reason));

        log.warn("Compensation started: sagaId={} reason={}", sagaId, reason);
        return saved;
    }

    @Transactional
    public PaymentSaga walletCredited(UUID sagaId) {
        return advance(sagaId, SagaStatus.WALLET_CREDITED, "WALLET_CREDITED",
                "Wallet credited back — compensation complete");
    }

    @Transactional
    public PaymentSaga paymentVoided(UUID sagaId) {
        return advance(sagaId, SagaStatus.PAYMENT_VOIDED, "PAYMENT_VOIDED",
                "Payment cancelled — compensation complete");
    }

    @Transactional
    public PaymentSaga compensated(UUID sagaId) {
        return advance(sagaId, SagaStatus.COMPENSATED, "SAGA_COMPENSATED",
                "Saga fully compensated — customer not charged");
    }

    @Transactional
    public PaymentSaga fail(UUID sagaId, String reason) {
        PaymentSaga saga = findOrThrow(sagaId);
        saga.advance(SagaStatus.FAILED);
        saga.setFailureReason(String.format("{\"reason\":\"%s\"}", reason));
        PaymentSaga saved = sagaRepository.save(saga);

        recordStep(sagaId, "SAGA_FAILED", "FAILED",
                String.format("{\"reason\":\"%s\"}", reason));

        log.error("Saga failed: sagaId={} reason={}", sagaId, reason);
        return saved;
    }


    @Transactional(readOnly = true)
    public PaymentSaga getSaga(UUID sagaId) {
        return findOrThrow(sagaId);
    }

    @Transactional(readOnly = true)
    public PaymentSaga getSagaByPaymentId(UUID paymentId) {
        return sagaRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new SagaNotFoundException(paymentId));
    }

    @Transactional(readOnly = true)
    public List<SagaStep> getSagaSteps(UUID sagaId) {
        return sagaStepRepository.findBySagaIdOrderByCreatedAtAsc(sagaId);
    }



    private PaymentSaga advance(UUID sagaId, SagaStatus nextStatus,
                                String stepName, String payload) {
        PaymentSaga saga = findOrThrow(sagaId);
        saga.advance(nextStatus);
        PaymentSaga saved = sagaRepository.save(saga);
        recordStep(sagaId, stepName, "COMPLETED", payload);
        log.info("Saga advanced: id={} status={}", sagaId, nextStatus);
        return saved;
    }

    private void recordStep(UUID sagaId, String stepName,
                            String status, String payload) {
        sagaStepRepository.save(
                SagaStep.of(sagaId, stepName, status, payload));
    }

    private PaymentSaga findOrThrow(UUID sagaId) {
        return sagaRepository.findById(sagaId)
                .orElseThrow(() -> new SagaNotFoundException(sagaId));
    }
}