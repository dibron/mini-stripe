package com.stripe.platform.saga.repository;

import com.stripe.platform.saga.domain.SagaStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SagaStepRepository extends JpaRepository<SagaStep, Long> {
    List<SagaStep> findBySagaIdOrderByCreatedAtAsc(UUID sagaId);
}