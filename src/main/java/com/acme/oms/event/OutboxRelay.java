package com.acme.oms.event;

import com.acme.oms.config.AppProperties;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox and publishes to Kafka, keyed by aggregate id so all events of one order stay ordered.
 * Delivery is at-least-once (a crash after send but before commit re-sends), so consumers must be idempotent.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafka, AppProperties props) {
        this.repository = repository;
        this.kafka = kafka;
        this.topic = props.kafka().eventsTopic();
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = repository.lockUnpublished(BATCH_SIZE);
        for (OutboxEvent event : batch) {
            try {
                kafka.send(topic, event.getAggregateId().toString(), event.getPayload()).get(10, TimeUnit.SECONDS);
                event.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Stop at the first failure to preserve ordering; already-sent rows are still marked and committed.
                log.warn("Outbox publish failed for event {} ({}), will retry: {}", event.getId(), event.getEventType(), e.toString());
                return;
            }
        }
    }
}
