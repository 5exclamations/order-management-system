package com.acme.oms.customer;

import com.acme.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(nullable = false)
    private String email;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 50)
    private String phone;
    @Column(nullable = false)
    private boolean active = true;

    protected Customer() {}

    public Customer(String email, String name, String phone) {
        this.email = email;
        this.name = name;
        this.phone = phone;
    }

    public void update(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
}
