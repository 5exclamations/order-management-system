package com.acme.oms.inventory;

import com.acme.oms.audit.AuditService;
import com.acme.oms.common.ApiException;
import com.acme.oms.common.PageResponse;
import com.acme.oms.common.RetryOnConflict;
import com.acme.oms.inventory.InventoryDtos.InventoryResponse;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository inventories;
    private final InventoryReservationRepository reservations;
    private final AuditService audit;

    public InventoryService(InventoryRepository inventories, InventoryReservationRepository reservations, AuditService audit) {
        this.inventories = inventories;
        this.reservations = reservations;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(UUID productId, int initialStock) {
        inventories.save(new Inventory(productId, initialStock));
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(UUID productId) {
        return InventoryResponse.from(find(productId));
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryResponse> list(Pageable pageable) {
        return PageResponse.of(inventories.findAll(pageable), InventoryResponse::from);
    }

    @RetryOnConflict
    @Transactional
    public InventoryResponse adjust(UUID productId, int delta, String reason) {
        Inventory inventory = find(productId);
        int before = inventory.getOnHand();
        inventory.adjust(delta);
        audit.record("INVENTORY_ADJUSTED", "Inventory", productId,
                "delta=" + delta + " onHand " + before + "->" + inventory.getOnHand() + " reason=" + reason);
        return InventoryResponse.from(inventories.saveAndFlush(inventory));
    }

    /** Sets stock aside for an order line. Joins the order transaction, so a failure rolls back the entire order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(UUID orderId, UUID productId, int quantity) {
        Inventory inventory = find(productId);
        inventory.reserve(quantity);
        inventories.saveAndFlush(inventory); // flush now: a lost optimistic-lock race surfaces here, not at commit
        reservations.save(new InventoryReservation(orderId, productId, quantity));
    }

    /** Returns held stock to the available pool. Idempotent: only HELD reservations are touched. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseForOrder(UUID orderId) {
        for (InventoryReservation r : held(orderId)) {
            Inventory inventory = find(r.getProductId());
            inventory.release(r.getQuantity());
            r.markReleased();
            inventories.save(inventory);
        }
    }

    /** Called on shipment: held stock physically leaves. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consumeForOrder(UUID orderId) {
        for (InventoryReservation r : held(orderId)) {
            Inventory inventory = find(r.getProductId());
            inventory.consume(r.getQuantity());
            r.markConsumed();
            inventories.save(inventory);
        }
    }

    private List<InventoryReservation> held(UUID orderId) {
        return reservations.findByOrderIdAndStatus(orderId, ReservationStatus.HELD).stream()
                .sorted(Comparator.comparing(InventoryReservation::getProductId))
                .toList();
    }

    private Inventory find(UUID productId) {
        return inventories.findById(productId).orElseThrow(() -> ApiException.notFound("Inventory for product", productId));
    }
}
