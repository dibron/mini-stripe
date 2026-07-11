package com.stripe.platform.wallet.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishFundsDebited(UUID paymentId, UUID walletId, UUID transactionId,
                                     Long amountCents, String currency,
                                     Long balanceAfterCents, String idempotencyKey) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId",           UUID.randomUUID().toString());
            event.put("eventType",         "FundsDebited");
            event.put("eventVersion",      "1.0");
            event.put("occurredAt",        Instant.now().toString());
            event.put("transactionId",     transactionId.toString());
            event.put("paymentId",         paymentId.toString());
            event.put("walletId",          walletId.toString());
            event.put("amountCents",       amountCents);
            event.put("currency",          currency);
            event.put("balanceAfterCents", balanceAfterCents);
            event.put("idempotencyKey",    idempotencyKey);

            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("wallet-events", walletId.toString(), payload);

            log.info("Published FundsDebited: walletId={} amountCents={}", walletId, amountCents);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize FundsDebited event: {}", e.getMessage(), e);
        }
    }
}
