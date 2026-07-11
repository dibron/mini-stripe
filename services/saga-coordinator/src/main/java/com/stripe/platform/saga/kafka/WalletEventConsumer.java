package com.stripe.platform.saga.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.platform.saga.domain.PaymentSaga;
import com.stripe.platform.saga.service.SagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletEventConsumer {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SagaService  sagaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "wallet-events", groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> event = objectMapper.readValue(record.value(), MAP_TYPE);
            String eventType = (String) event.get("eventType");

            if (!"FundsDebited".equals(eventType)) return;

            UUID paymentId = UUID.fromString((String) event.get("paymentId"));

            log.info("Saga: received FundsDebited for paymentId={}", paymentId);

            PaymentSaga saga = sagaService.getSagaByPaymentId(paymentId);
            sagaService.walletDebited(saga.getId());
            sagaService.complete(saga.getId());

        } catch (Exception e) {
            log.error("Failed to process FundsDebited in saga: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
