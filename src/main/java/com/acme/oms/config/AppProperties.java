package com.acme.oms.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed, validated application settings. The app refuses to start if they are missing or weak. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid Security security, @Valid Orders orders, @Valid Kafka kafka, @Valid Outbox outbox) {

    public record Security(
            @NotBlank(message = "JWT_SECRET must be set") @Size(min = 32, message = "JWT secret must be at least 32 characters") String jwtSecret,
            @NotNull Duration tokenTtl) {}

    /** reservationTtl: how long an unpaid order keeps its stock reserved. */
    public record Orders(@NotNull Duration reservationTtl) {}

    public record Kafka(@NotBlank String eventsTopic) {}

    public record Outbox(boolean enabled, @NotNull Duration pollInterval) {}
}
