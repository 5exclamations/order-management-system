package com.acme.oms.order;

import com.acme.oms.config.AppProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Stops unpaid orders from holding stock forever. Safe to run on several instances at once: expiry is a
 * state-checked, optimistically-locked transition, so a given order is only expired once.
 */
@Component
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderRepository orders;
    private final OrderService service;
    private final AppProperties props;

    public OrderExpiryJob(OrderRepository orders, OrderService service, AppProperties props) {
        this.orders = orders;
        this.service = service;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.orders.expiry-scan-interval}", initialDelayString = "${app.orders.expiry-scan-interval}")
    public void expireStaleOrders() {
        expireOlderThan(Instant.now().minus(props.orders().reservationTtl()));
    }

    /** Package-visible so tests can drive it with an explicit cutoff. Returns the number of orders expired. */
    public int expireOlderThan(Instant cutoff) {
        List<UUID> stale = orders.findIdsByStatusCreatedBefore(OrderStatus.PENDING, cutoff);
        int expired = 0;
        for (UUID id : stale) {
            try {
                if (service.expire(id)) {
                    expired++;
                }
            } catch (Exception e) {
                log.warn("Could not expire order {}: {}", id, e.toString()); // picked up again on the next run
            }
        }
        if (expired > 0) {
            log.info("Expired {} unpaid order(s) and released their stock", expired);
        }
        return expired;
    }
}
