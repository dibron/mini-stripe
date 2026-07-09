package com.stripe.platform.saga.api;

import com.stripe.platform.saga.domain.PaymentSaga;
import com.stripe.platform.saga.domain.SagaStatus;
import com.stripe.platform.saga.domain.SagaStep;
import com.stripe.platform.saga.service.SagaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sagas")
@RequiredArgsConstructor
public class SagaController {

    private final SagaService sagaService;

    // POST /api/v1/sagas/payment — start a new payment saga
    @PostMapping("/payment")
    public ResponseEntity<SagaResponse> startPaymentSaga(
            @Valid @RequestBody StartSagaRequest request) {
        PaymentSaga saga = sagaService.startSaga(
                request.paymentId(),
                request.walletId(),
                request.amountCents(),
                request.currency()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SagaResponse.from(saga));
    }

    // GET /api/v1/sagas/{sagaId} — get saga state
    @GetMapping("/{sagaId}")
    public ResponseEntity<SagaResponse> getSaga(@PathVariable UUID sagaId) {
        return ResponseEntity.ok(SagaResponse.from(sagaService.getSaga(sagaId)));
    }

    // GET /api/v1/sagas/payment/{paymentId} — find saga by payment
    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<SagaResponse> getSagaByPayment(
            @PathVariable UUID paymentId) {
        return ResponseEntity.ok(
                SagaResponse.from(sagaService.getSagaByPaymentId(paymentId)));
    }

    // GET /api/v1/sagas/{sagaId}/steps — full audit trail
    @GetMapping("/{sagaId}/steps")
    public ResponseEntity<List<StepResponse>> getSagaSteps(
            @PathVariable UUID sagaId) {
        return ResponseEntity.ok(
                sagaService.getSagaSteps(sagaId).stream()
                        .map(StepResponse::from)
                        .toList());
    }

    @PostMapping("/{sagaId}/advance")
    public ResponseEntity<SagaResponse> advance(
            @PathVariable UUID sagaId,
            @RequestParam SagaStatus to) {
        PaymentSaga saga = switch (to) {
            case PAYMENT_INITIATED -> sagaService.paymentInitiated(sagaId);
            case WALLET_DEBITED    -> sagaService.walletDebited(sagaId);
            case LEDGER_RECORDED   -> sagaService.ledgerRecorded(sagaId);
            case PAYMENT_CAPTURED  -> sagaService.paymentCaptured(sagaId);
            case COMPLETED         -> sagaService.complete(sagaId);
            case WALLET_CREDITED   -> sagaService.walletCredited(sagaId);
            case PAYMENT_VOIDED    -> sagaService.paymentVoided(sagaId);
            case COMPENSATED       -> sagaService.compensated(sagaId);
            default -> throw new IllegalArgumentException(
                    "Cannot advance to " + to + " via this endpoint");
        };
        return ResponseEntity.ok(SagaResponse.from(saga));
    }

    // POST /api/v1/sagas/{sagaId}/compensate — trigger compensation
    @PostMapping("/{sagaId}/compensate")
    public ResponseEntity<SagaResponse> compensate(
            @PathVariable UUID sagaId,
            @RequestParam String reason) {
        return ResponseEntity.ok(
                SagaResponse.from(sagaService.startCompensation(sagaId, reason)));
    }

    // ── DTOs ────────────────────────────────────────────────

    public record StartSagaRequest(
            @NotNull UUID paymentId,
            @NotNull UUID walletId,
            @NotNull @Positive Long amountCents,
            @NotBlank String currency
    ) {}

    public record SagaResponse(
            UUID      id,
            UUID      paymentId,
            UUID      walletId,
            Long      amountCents,
            String    currency,
            String    status,
            String    failureReason,
            Instant   createdAt,
            Instant   updatedAt,
            Instant   completedAt
    ) {
        public static SagaResponse from(PaymentSaga s) {
            return new SagaResponse(
                    s.getId(), s.getPaymentId(), s.getWalletId(),
                    s.getAmountCents(), s.getCurrency(),
                    s.getStatus().name(), s.getFailureReason(),
                    s.getCreatedAt(), s.getUpdatedAt(), s.getCompletedAt()
            );
        }
    }

    public record StepResponse(
            Long    id,
            String  stepName,
            String  status,
            String  payload,
            Instant createdAt
    ) {
        public static StepResponse from(SagaStep s) {
            return new StepResponse(
                    s.getId(), s.getStepName(),
                    s.getStatus(), s.getPayload(), s.getCreatedAt()
            );
        }
    }
}