package dev.adamgrochulski.javamon.app.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Originy dopuszczone przez CORS. Inne lokalnie, inne na serwerze. */
@ConfigurationProperties(prefix = "javamon.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}