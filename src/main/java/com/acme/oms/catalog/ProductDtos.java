package com.acme.oms.catalog;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ProductDtos {
    private ProductDtos() {}

    public record CreateProductRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "letters, digits, '.', '_' and '-' only") String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999.99") @Digits(integer = 7, fraction = 2) BigDecimal price,
            @Min(0) Integer initialStock) {}

    public record UpdateProductRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999.99") @Digits(integer = 7, fraction = 2) BigDecimal price) {}

    public record ProductResponse(UUID id, String sku, String name, String description, BigDecimal price,
                                  boolean active, Instant createdAt, Instant updatedAt, Long version) {
        static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getPrice(),
                    p.isActive(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
        }
    }
}
