package com.acme.oms.event;

import com.acme.oms.common.RequestIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox writer. Events are inserted in the same transaction as the state change, which removes the
 * dual-write problem (DB committed but Kafka publish lost, or vice versa). {@link OutboxRelay} ships them to Kafka.
 */
@Service
public class EventPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper mapper;

    public EventPublisher(OutboxEventRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String type, UUID aggregateId, Map<String, Object> data) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("type", type);
        envelope.put("aggregateId", aggregateId);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        envelope.put("data", data);
        try {
            repository.save(new OutboxEvent(aggregateId, type, mapper.writeValueAsString(envelope)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event " + type, e);
        }
    }
}
