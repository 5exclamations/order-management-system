package com.acme.oms.security;

/** SpEL expressions for {@code @PreAuthorize}. */
public final class Roles {
    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String STAFF = "hasAnyRole('ADMIN','STAFF')";

    private Roles() {}
}
