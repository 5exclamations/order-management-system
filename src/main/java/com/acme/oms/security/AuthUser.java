package com.acme.oms.security;

import java.util.UUID;

/** The authenticated caller, extracted from the JWT. */
public record AuthUser(UUID userId, String username, Role role, UUID customerId) {

    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.STAFF;
    }

    /** Staff may touch any customer's data; a customer only their own. */
    public boolean canAccessCustomer(UUID targetCustomerId) {
        return isStaff() || (customerId != null && customerId.equals(targetCustomerId));
    }
}
