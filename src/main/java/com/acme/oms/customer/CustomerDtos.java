package com.acme.oms.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record CreateCustomerRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 50) String phone) {}

    public record UpdateCustomerRequest(@NotBlank @Size(max = 200) String name, @Size(max = 50) String phone) {}

    public record CustomerResponse(UUID id, String email, String name, String phone, boolean active,
                                   Instant createdAt, Instant updatedAt, Long version) {
        static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getEmail(), c.getName(), c.getPhone(), c.isActive(),
                    c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
        }
    }
}
