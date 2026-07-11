package com.stripe.platform.payment.kafka;

import com.stripe.platform.payment.domain.OutboxEvent;
import com.stripe.platform.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxEventRepository  outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxEvent> pending = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate
                        .send("payment-events", event.getAggregateId().toString(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);

                event.markPublished();
                outboxRepository.save(event);

                log.info("Outbox relayed: eventType={} paymentId={}",
                        event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Outbox relay failed for event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
