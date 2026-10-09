package com.acme.oms.event;

import com.acme.oms.payment.RefundService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Event-driven refund settlement. Consumes the domain event stream and reacts to REFUND_REQUESTED.
 * Exceptions propagate to the container's error handler: retried with back-off, then sent to the dead-letter topic.
 */
@Component
public class RefundEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RefundEventConsumer.class);

    private final RefundService refunds;
    private final ObjectMapper mapper;

    public RefundEventConsumer(RefundService refunds, ObjectMapper mapper) {
        this.refunds = refunds;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${app.kafka.events-topic}", groupId = "oms-refund-processor")
    public void onEvent(String message) throws Exception {
        JsonNode event = mapper.readTree(message);
        if (!"REFUND_REQUESTED".equals(event.path("type").asText())) {
            return;
        }
        UUID refundId = UUID.fromString(event.path("data").path("refundId").asText());
        log.info("Processing REFUND_REQUESTED refundId={}", refundId);
        refunds.process(refundId);
    }
}
