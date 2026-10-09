package com.acme.oms.inventory;

import com.acme.oms.common.PageResponse;
import com.acme.oms.inventory.InventoryDtos.AdjustInventoryRequest;
import com.acme.oms.inventory.InventoryDtos.InventoryResponse;
import com.acme.oms.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory")
@PreAuthorize(Roles.STAFF)
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @Operation(summary = "List stock levels")
    @GetMapping
    public PageResponse<InventoryResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @Operation(summary = "Stock level for a product: on hand, reserved, available")
    @GetMapping("/{productId}")
    public InventoryResponse get(@PathVariable UUID productId) {
        return service.get(productId);
    }

    @Operation(summary = "Receive stock (positive delta) or write off (negative delta)")
    @PostMapping("/{productId}/adjustments")
    public InventoryResponse adjust(@PathVariable UUID productId, @Valid @RequestBody AdjustInventoryRequest request) {
        return service.adjust(productId, request.delta(), request.reason());
    }
}
