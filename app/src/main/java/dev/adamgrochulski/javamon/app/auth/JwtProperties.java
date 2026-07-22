package dev.adamgrochulski.javamon.app.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Wiąże sekcję javamon.jwt z application.yml. Sekret przychodzi ze zmiennej
 * środowiskowej JWT_SECRET — w repozytorium nie ma i nie może być jego wartości.
 */
@ConfigurationProperties(prefix = "javamon.jwt")
public record JwtProperties(String secret, Duration ttl, Duration guestTtl) {
}

