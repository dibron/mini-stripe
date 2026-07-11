package com.stripe.platform.wallet.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.platform.wallet.domain.Transaction;
import com.stripe.platform.wallet.service.InsufficientFundsException;
import com.stripe.platform.wallet.service.WalletService;
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

    private final WalletService        walletService;
    private final WalletEventPublisher publisher;
    private final ObjectMapper         objectMapper;

    @KafkaListener(topics = "payment-events", groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> event = objectMapper.readValue(record.value(), MAP_TYPE);
            String eventType = (String) event.get("eventType");

            if (!"PaymentInitiated".equals(eventType)) return;

            UUID   walletId       = UUID.fromString((String) event.get("walletId"));
            UUID   paymentId      = UUID.fromString((String) event.get("paymentId"));
            Long   amountCents    = ((Number) event.get("amountCents")).longValue();
            String currency       = (String) event.get("currency");
            String idempotencyKey = "wallet-debit-" + paymentId;

            log.info("Processing PaymentInitiated: paymentId={} walletId={} amount={}",
                    paymentId, walletId, amountCents);

            Transaction txn = walletService.debitWallet(
                    walletId, amountCents, currency, idempotencyKey, paymentId);

            Long balanceAfter = walletService.getBalance(walletId);

            publisher.publishFundsDebited(
                    paymentId, walletId, txn.getId(),
                    amountCents, currency, balanceAfter, idempotencyKey);

        } catch (InsufficientFundsException e) {
            log.error("Insufficient funds — skipping debit: {}", e.getMessage());
            // Phase 3: trigger saga compensation here
        } catch (Exception e) {
            log.error("Failed to process payment-events record: {}", e.getMessage(), e);
            throw new RuntimeException(e); // rethrow → Kafka retries
        }
    }
}
