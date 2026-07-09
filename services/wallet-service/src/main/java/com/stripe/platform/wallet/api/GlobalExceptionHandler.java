package com.stripe.platform.wallet.api;

import com.stripe.platform.wallet.service.InsufficientFundsException;
import com.stripe.platform.wallet.service.WalletNotFoundException;
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


    @ExceptionHandler(WalletNotFoundException.class)
    public ProblemDetail handleWalletNotFound(WalletNotFoundException ex) {
        log.warn("Wallet not found: {}", ex.getMessage());

        ProblemDetail detail = ProblemDetail
                .forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("https://stripe-platform/errors/wallet-not-found"));
        detail.setTitle("Wallet Not Found");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        log.warn("Insufficient funds: {}", ex.getMessage());

        ProblemDetail detail = ProblemDetail
                .forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        detail.setType(URI.create("https://stripe-platform/errors/insufficient-funds"));
        detail.setTitle("Insufficient Funds");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent modification on wallet");

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Wallet was modified by a concurrent request. Please retry.");
        detail.setType(URI.create("https://stripe-platform/errors/concurrent-modification"));
        detail.setTitle("Concurrent Modification");
        detail.setProperty("timestamp", Instant.now());
        detail.setProperty("retryable", true);
        return detail;
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        log.warn("Validation failed: {}", message);

        ProblemDetail detail = ProblemDetail
                .forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        detail.setType(URI.create("https://stripe-platform/errors/validation-failed"));
        detail.setTitle("Validation Failed");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }
}