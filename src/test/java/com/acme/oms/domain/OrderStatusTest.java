package com.acme.oms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.oms.common.ApiException;
import com.acme.oms.order.CustomerOrder;
import com.acme.oms.order.OrderStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void happyPathIsAllowed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
    }

    @Test
    void terminalStatesAllowNothing() {
        for (OrderStatus s : new OrderStatus[] {OrderStatus.CANCELLED, OrderStatus.REFUNDED}) {
            assertThat(s.isTerminal()).isTrue();
            for (OrderStatus n : OrderStatus.values()) {
                assertThat(s.canTransitionTo(n)).isFalse();
            }
        }
    }

    @Test
    void shippedOrdersCannotBeCancelled() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }

    @Test
    void illegalTransitionOnEntityThrowsConflict() {
        CustomerOrder order = new CustomerOrder(UUID.randomUUID());
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.SHIPPED))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("PENDING to SHIPPED");
    }
}
