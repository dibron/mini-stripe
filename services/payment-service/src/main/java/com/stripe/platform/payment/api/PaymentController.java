package com.stripe.platform.payment.api;

import com.stripe.platform.payment.domain.Payment;
import com.stripe.platform.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request) {
        Payment payment = paymentService.initiatePayment(
            request.walletId(), request.merchantId(),
            request.amountCents(), request.currency(),
            request.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(PaymentResponse.from(payment));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(PaymentResponse.from(paymentService.getPayment(paymentId)));
    }

    @PostMapping("/{paymentId}/capture")
    public ResponseEntity<PaymentResponse> capturePayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(PaymentResponse.from(paymentService.capturePayment(paymentId)));
    }

    // DTO records
    public record InitiatePaymentRequest(
        @NotNull UUID walletId,
        @NotNull UUID merchantId,
        @NotNull @Positive Long amountCents,
        @NotBlank String currency,
        @NotBlank String idempotencyKey
    ) {}

    public record PaymentResponse(
        UUID id, UUID walletId, UUID merchantId,
        Long amountCents, String currency, String status,
        String idempotencyKey, String createdAt
    ) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(
                p.getId(), p.getWalletId(), p.getMerchantId(),
                p.getAmountCents(), p.getCurrency(), p.getStatus().name(),
                p.getIdempotencyKey(), p.getCreatedAt().toString());
        }
    }
}
