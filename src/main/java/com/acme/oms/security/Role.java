package com.acme.oms.security;

public enum Role {
    /** Everything, including audit logs. */
    ADMIN,
    /** Back-office: catalog, inventory, fulfilment, refunds, customers. */
    STAFF,
    /** Self-service: own profile and own orders only. */
    CUSTOMER
}
