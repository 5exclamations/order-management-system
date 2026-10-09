package com.acme.oms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.oms.common.ApiException;
import com.acme.oms.inventory.Inventory;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryTest {

    private final Inventory inv = new Inventory(UUID.randomUUID(), 10);

    @Test
    void reserveReducesAvailableOnly() {
        inv.reserve(4);
        assertThat(inv.available()).isEqualTo(6);
        assertThat(inv.getOnHand()).isEqualTo(10);
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        inv.reserve(8);
        assertThatThrownBy(() -> inv.reserve(3)).isInstanceOf(ApiException.class).hasMessageContaining("Insufficient");
        assertThat(inv.getReserved()).isEqualTo(8);
    }

    @Test
    void consumeRemovesFromHandAndReserved() {
        inv.reserve(4);
        inv.consume(4);
        assertThat(inv.getOnHand()).isEqualTo(6);
        assertThat(inv.getReserved()).isZero();
    }

    @Test
    void releaseRestoresAvailability() {
        inv.reserve(4);
        inv.release(4);
        assertThat(inv.available()).isEqualTo(10);
    }

    @Test
    void adjustmentCannotDipIntoReservedStock() {
        inv.reserve(7);
        assertThatThrownBy(() -> inv.adjust(-4)).isInstanceOf(ApiException.class);
        inv.adjust(-3);
        assertThat(inv.getOnHand()).isEqualTo(7);
    }
}
