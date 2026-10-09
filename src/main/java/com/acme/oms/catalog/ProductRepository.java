package com.acme.oms.catalog;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySkuIgnoreCase(String sku);

    @Query("""
            select p from Product p
            where (:includeInactive = true or p.active = true)
              and lower(p.name) like lower(concat('%', :q, '%'))
            """)
    Page<Product> search(@Param("q") String q, @Param("includeInactive") boolean includeInactive, Pageable pageable);
}
