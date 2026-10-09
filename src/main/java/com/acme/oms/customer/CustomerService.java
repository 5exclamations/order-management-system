package com.acme.oms.customer;

import com.acme.oms.audit.AuditService;
import com.acme.oms.common.ApiException;
import com.acme.oms.common.PageResponse;
import com.acme.oms.common.RetryOnConflict;
import com.acme.oms.customer.CustomerDtos.CreateCustomerRequest;
import com.acme.oms.customer.CustomerDtos.CustomerResponse;
import com.acme.oms.customer.CustomerDtos.UpdateCustomerRequest;
import com.acme.oms.security.AuthUser;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final AuditService audit;

    public CustomerService(CustomerRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "A customer with this email already exists");
        }
        Customer customer = repository.save(new Customer(email, request.name().trim(), request.phone()));
        audit.record("CUSTOMER_CREATED", "Customer", customer.getId(), "email=" + email);
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id, AuthUser actor) {
        return CustomerResponse.from(load(id, actor));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(Pageable pageable) {
        return PageResponse.of(repository.findAll(pageable), CustomerResponse::from);
    }

    @RetryOnConflict
    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request, AuthUser actor) {
        Customer customer = load(id, actor);
        customer.update(request.name().trim(), request.phone());
        audit.record("CUSTOMER_UPDATED", "Customer", id, "name=" + request.name());
        return CustomerResponse.from(repository.saveAndFlush(customer));
    }

    @RetryOnConflict
    @Transactional
    public CustomerResponse setActive(UUID id, boolean active) {
        Customer customer = repository.findById(id).orElseThrow(() -> ApiException.notFound("Customer", id));
        if (active) customer.activate(); else customer.deactivate();
        audit.record(active ? "CUSTOMER_ACTIVATED" : "CUSTOMER_DEACTIVATED", "Customer", id, null);
        return CustomerResponse.from(repository.saveAndFlush(customer));
    }

    /** Customers see only themselves; asking for anyone else looks like "not found". */
    private Customer load(UUID id, AuthUser actor) {
        if (!actor.canAccessCustomer(id)) {
            throw ApiException.notFound("Customer", id);
        }
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Customer", id));
    }
}
