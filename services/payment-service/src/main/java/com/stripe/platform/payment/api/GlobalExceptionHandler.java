package com.stripe.platform.payment.api;

import com.stripe.platform.payment.domain.IllegalPaymentTransitionException;
import com.stripe.platform.payment.service.PaymentNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalPaymentTransitionException.class)
    public ProblemDetail handleIllegalTransition(IllegalPaymentTransitionException ex) {
        log.warn("Illegal payment transition: {}", ex.getMessage());
        ProblemDetail d = ProblemDetail
                .forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        d.setType(URI.create("https://stripe-platform/errors/illegal-state-transition"));
        d.setTitle("Illegal Payment State Transition");
        d.setProperty("timestamp", Instant.now());
        return d;
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handleNotFound(PaymentNotFoundException ex) {
        ProblemDetail d = ProblemDetail
                .forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        d.setType(URI.create("https://stripe-platform/errors/payment-not-found"));
        d.setTitle("Payment Not Found");
        d.setProperty("timestamp", Instant.now());
        return d;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent modification on payment");
        ProblemDetail d = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Payment was modified concurrently. Please retry.");
        d.setType(URI.create("https://stripe-platform/errors/concurrent-modification"));
        d.setTitle("Concurrent Modification");
        d.setProperty("timestamp", Instant.now());
        d.setProperty("retryable", true);
        return d;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("Validation failed");
        ProblemDetail d = ProblemDetail
                .forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
        d.setType(URI.create("https://stripe-platform/errors/validation-failed"));
        d.setTitle("Validation Failed");
        d.setProperty("timestamp", Instant.now());
        return d;
    }
}