package com.stripe.platform.saga.repository;

import com.stripe.platform.saga.domain.PaymentSaga;
import com.stripe.platform.saga.domain.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaRepository extends JpaRepository<PaymentSaga, UUID> {

    Optional<PaymentSaga> findByPaymentId(UUID paymentId);

    List<PaymentSaga> findByStatus(SagaStatus status);
}