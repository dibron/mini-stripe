package com.stripe.platform.saga.api;

import com.stripe.platform.saga.service.SagaNotFoundException;
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

    @ExceptionHandler(SagaNotFoundException.class)
    public ProblemDetail handleNotFound(SagaNotFoundException ex) {
        ProblemDetail d = ProblemDetail
                .forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        d.setType(URI.create("https://stripe-platform/errors/saga-not-found"));
        d.setTitle("Saga Not Found");
        d.setProperty("timestamp", Instant.now());
        return d;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent saga modification");
        ProblemDetail d = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Saga modified concurrently. Please retry.");
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