package com.acme.oms.auth;

import com.acme.oms.audit.AuditService;
import com.acme.oms.auth.AuthDtos.LoginRequest;
import com.acme.oms.auth.AuthDtos.RegisterRequest;
import com.acme.oms.auth.AuthDtos.TokenResponse;
import com.acme.oms.common.ApiException;
import com.acme.oms.config.AppProperties;
import com.acme.oms.customer.Customer;
import com.acme.oms.customer.CustomerRepository;
import com.acme.oms.security.Role;
import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties props;
    private final AuditService audit;

    public AuthService(AppUserRepository users, CustomerRepository customers, PasswordEncoder encoder,
                       JwtEncoder jwtEncoder, AppProperties props, AuditService audit) {
        this.users = users;
        this.customers = customers;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.audit = audit;
    }

    /** Self-service sign-up: creates the customer profile and a CUSTOMER login in one transaction. */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByUsernameIgnoreCase(email) || customers.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }
        Customer customer = customers.save(new Customer(email, request.name().trim(), request.phone()));
        AppUser user = users.save(new AppUser(email, encoder.encode(request.password()), Role.CUSTOMER, customer.getId()));
        audit.record("CUSTOMER_REGISTERED", "Customer", customer.getId(), "email=" + email);
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        AppUser user = users.findByUsernameIgnoreCase(request.username().trim())
                .filter(AppUser::isEnabled)
                .filter(u -> encoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password"));
        return issueToken(user);
    }

    private TokenResponse issueToken(AppUser user) {
        Instant now = Instant.now();
        var ttl = props.security().tokenTtl();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("uid", user.getId().toString())
                .claim("roles", List.of(user.getRole().name()));
        if (user.getCustomerId() != null) {
            claims.claim("customerId", user.getCustomerId().toString());
        }
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds(), user.getRole(), user.getCustomerId());
    }
}
