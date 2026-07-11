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
public class PaymentEventConsumer {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SagaService  sagaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-events", groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> event = objectMapper.readValue(record.value(), MAP_TYPE);
            String eventType = (String) event.get("eventType");

            if (!"PaymentInitiated".equals(eventType)) return;

            UUID   paymentId  = UUID.fromString((String) event.get("paymentId"));
            UUID   walletId   = UUID.fromString((String) event.get("walletId"));
            Long   amount     = ((Number) event.get("amountCents")).longValue();
            String currency   = (String) event.get("currency");

            log.info("Saga: received PaymentInitiated for paymentId={}", paymentId);

            PaymentSaga saga = sagaService.startSaga(paymentId, walletId, amount, currency);
            sagaService.paymentInitiated(saga.getId());

        } catch (Exception e) {
            log.error("Failed to process PaymentInitiated in saga: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
