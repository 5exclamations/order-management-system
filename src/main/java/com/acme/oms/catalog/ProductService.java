package com.acme.oms.catalog;

import com.acme.oms.audit.AuditService;
import com.acme.oms.catalog.ProductDtos.CreateProductRequest;
import com.acme.oms.catalog.ProductDtos.ProductResponse;
import com.acme.oms.catalog.ProductDtos.UpdateProductRequest;
import com.acme.oms.common.ApiException;
import com.acme.oms.common.PageResponse;
import com.acme.oms.common.RetryOnConflict;
import com.acme.oms.inventory.InventoryService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final InventoryService inventory;
    private final AuditService audit;

    public ProductService(ProductRepository repository, InventoryService inventory, AuditService audit) {
        this.repository = repository;
        this.inventory = inventory;
        this.audit = audit;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = request.sku().trim();
        if (repository.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("SKU_ALREADY_EXISTS", "A product with SKU '" + sku + "' already exists");
        }
        Product product = repository.saveAndFlush(new Product(sku, request.name().trim(), request.description(), request.price()));
        inventory.initialize(product.getId(), request.initialStock() == null ? 0 : request.initialStock());
        audit.record("PRODUCT_CREATED", "Product", product.getId(), "sku=" + sku + " price=" + request.price());
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(repository.findById(id).orElseThrow(() -> ApiException.notFound("Product", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String q, boolean includeInactive, Pageable pageable) {
        return PageResponse.of(repository.search(q == null ? "" : q, includeInactive, pageable), ProductResponse::from);
    }

    /** Price changes never touch existing orders: order lines snapshot the price at purchase time. */
    @RetryOnConflict
    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = repository.findById(id).orElseThrow(() -> ApiException.notFound("Product", id));
        product.update(request.name().trim(), request.description(), request.price());
        audit.record("PRODUCT_UPDATED", "Product", id, "price=" + request.price());
        return ProductResponse.from(repository.saveAndFlush(product));
    }

    @RetryOnConflict
    @Transactional
    public ProductResponse setActive(UUID id, boolean active) {
        Product product = repository.findById(id).orElseThrow(() -> ApiException.notFound("Product", id));
        product.setActive(active);
        audit.record(active ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED", "Product", id, null);
        return ProductResponse.from(repository.saveAndFlush(product));
    }
}
