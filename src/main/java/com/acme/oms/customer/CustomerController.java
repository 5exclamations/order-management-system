package com.acme.oms.customer;

import com.acme.oms.common.ApiException;
import com.acme.oms.common.PageResponse;
import com.acme.oms.customer.CustomerDtos.CreateCustomerRequest;
import com.acme.oms.customer.CustomerDtos.CustomerResponse;
import com.acme.oms.customer.CustomerDtos.UpdateCustomerRequest;
import com.acme.oms.security.AuthUser;
import com.acme.oms.security.Roles;
import com.acme.oms.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @Operation(summary = "Create a customer profile (staff)")
    @PostMapping
    @PreAuthorize(Roles.STAFF)
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return service.create(request);
    }

    @Operation(summary = "List customers (staff)")
    @GetMapping
    @PreAuthorize(Roles.STAFF)
    public PageResponse<CustomerResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @Operation(summary = "The caller's own customer profile")
    @GetMapping("/me")
    public CustomerResponse me() {
        AuthUser user = SecurityUtils.requireUser();
        if (user.customerId() == null) {
            throw ApiException.notFound("Customer profile", user.username());
        }
        return service.get(user.customerId(), user);
    }

    @Operation(summary = "Get a customer (staff, or the customer themself)")
    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return service.get(id, SecurityUtils.requireUser());
    }

    @Operation(summary = "Update name/phone (staff, or the customer themself)")
    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return service.update(id, request, SecurityUtils.requireUser());
    }

    @Operation(summary = "Deactivate a customer; they can no longer place orders (staff)")
    @PostMapping("/{id}/deactivate")
    @PreAuthorize(Roles.STAFF)
    public CustomerResponse deactivate(@PathVariable UUID id) {
        return service.setActive(id, false);
    }

    @Operation(summary = "Reactivate a customer (staff)")
    @PostMapping("/{id}/activate")
    @PreAuthorize(Roles.STAFF)
    public CustomerResponse activate(@PathVariable UUID id) {
        return service.setActive(id, true);
    }
}
