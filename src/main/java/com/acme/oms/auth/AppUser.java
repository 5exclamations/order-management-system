package com.acme.oms.auth;

import com.acme.oms.common.BaseEntity;
import com.acme.oms.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    private UUID customerId;

    @Column(nullable = false)
    private boolean enabled = true;

    protected AppUser() {}

    public AppUser(String username, String passwordHash, Role role, UUID customerId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.customerId = customerId;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public UUID getCustomerId() { return customerId; }
    public boolean isEnabled() { return enabled; }
}
