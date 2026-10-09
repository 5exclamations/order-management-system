package com.acme.oms.config;

import com.acme.oms.auth.AppUser;
import com.acme.oms.auth.AppUserRepository;
import com.acme.oms.catalog.ProductDtos.CreateProductRequest;
import com.acme.oms.catalog.ProductRepository;
import com.acme.oms.catalog.ProductService;
import com.acme.oms.security.Role;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Dev profile only: demo admin/staff users and a few products. Password comes from DEV_SEED_PASSWORD. */
@Component
@Profile("dev")
public class DevDataInitializer {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final ProductService productService;
    private final ProductRepository products;
    private final String password;

    public DevDataInitializer(AppUserRepository users, PasswordEncoder encoder, ProductService productService,
                              ProductRepository products, @Value("${DEV_SEED_PASSWORD:DevPassw0rd!}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.productService = productService;
        this.products = products;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!users.existsByUsernameIgnoreCase("admin@oms.local")) {
            users.save(new AppUser("admin@oms.local", encoder.encode(password), Role.ADMIN, null));
            users.save(new AppUser("staff@oms.local", encoder.encode(password), Role.STAFF, null));
        }
        if (products.count() == 0) {
            productService.create(new CreateProductRequest("KEYBOARD-01", "Mechanical Keyboard", "Tenkeyless", new BigDecimal("89.90"), 50));
            productService.create(new CreateProductRequest("MOUSE-01", "Wireless Mouse", "Ergonomic", new BigDecimal("39.00"), 100));
            productService.create(new CreateProductRequest("MONITOR-01", "27in Monitor", "4K IPS", new BigDecimal("329.00"), 5));
        }
    }
}
