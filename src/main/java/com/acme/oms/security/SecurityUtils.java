package com.acme.oms.security;

import com.acme.oms.common.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<AuthUser> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            List<String> roles = jwt.getClaimAsStringList("roles");
            String customerId = jwt.getClaimAsString("customerId");
            return Optional.of(new AuthUser(
                    UUID.fromString(jwt.getClaimAsString("uid")),
                    jwt.getSubject(),
                    Role.valueOf(roles.get(0)),
                    customerId == null ? null : UUID.fromString(customerId)));
        }
        return Optional.empty();
    }

    public static AuthUser requireUser() {
        return currentUser().orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication required"));
    }

    /** Name recorded in audit entries; "system" for scheduled jobs and event consumers. */
    public static String actorName() {
        return currentUser().map(AuthUser::username).orElse("system");
    }
}
