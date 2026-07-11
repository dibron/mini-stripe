package com.stripe.platform.ledger.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.platform.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletEventConsumer {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LedgerService  ledgerService;
    private final ObjectMapper   objectMapper;

    @KafkaListener(topics = "wallet-events", groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> event = objectMapper.readValue(record.value(), MAP_TYPE);
            String eventType = (String) event.get("eventType");

            if (!"FundsDebited".equals(eventType) && !"FundsCredited".equals(eventType)) return;

            UUID    walletId       = UUID.fromString((String) event.get("walletId"));
            String  idempotencyKey = (String) event.get("eventId");
            Instant occurredAt     = Instant.parse((String) event.get("occurredAt"));

            ledgerService.appendEvent(
                    walletId,
                    eventType,
                    (String) event.get("eventVersion"),
                    record.value(),
                    idempotencyKey,
                    occurredAt);

            log.info("Ledger recorded: eventType={} walletId={}", eventType, walletId);

        } catch (Exception e) {
            log.error("Failed to process wallet-events record: {}", e.getMessage(), e);
            throw new RuntimeException(e); // rethrow → Kafka retries
        }
    }
}
