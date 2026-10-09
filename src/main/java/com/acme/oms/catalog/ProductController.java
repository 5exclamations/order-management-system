package com.acme.oms.catalog;

import com.acme.oms.catalog.ProductDtos.CreateProductRequest;
import com.acme.oms.catalog.ProductDtos.ProductResponse;
import com.acme.oms.catalog.ProductDtos.UpdateProductRequest;
import com.acme.oms.common.PageResponse;
import com.acme.oms.security.Roles;
import com.acme.oms.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Catalog")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @Operation(summary = "Create a product and its inventory record (staff)")
    @PostMapping
    @PreAuthorize(Roles.STAFF)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Search products by name; inactive ones only for staff with includeInactive=true")
    @GetMapping
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "false") boolean includeInactive,
                                                @PageableDefault(size = 20) Pageable pageable) {
        boolean staff = SecurityUtils.requireUser().isStaff();
        return service.search(q, includeInactive && staff, pageable);
    }

    @Operation(summary = "Get a product")
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Update name, description and price (staff)")
    @PutMapping("/{id}")
    @PreAuthorize(Roles.STAFF)
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Deactivate (soft-delete) a product (staff)")
    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.STAFF)
    public ProductResponse deactivate(@PathVariable UUID id) {
        return service.setActive(id, false);
    }

    @Operation(summary = "Reactivate a product (staff)")
    @PostMapping("/{id}/activate")
    @PreAuthorize(Roles.STAFF)
    public ProductResponse activate(@PathVariable UUID id) {
        return service.setActive(id, true);
    }
}
