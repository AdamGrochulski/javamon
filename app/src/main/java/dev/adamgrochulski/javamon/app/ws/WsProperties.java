package dev.adamgrochulski.javamon.app.ws;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Czas na przysłanie ramki AUTH. Z konfiguracji, nie ze stałej — test nie może
 * czekać dziesięciu sekund tylko po to, żeby sprawdzić, że timeout działa.
 */
@ConfigurationProperties(prefix = "javamon.ws")
public record WsProperties(Duration authTimeout) {

    public WsProperties {
        if (authTimeout == null) {
            authTimeout = Duration.ofSeconds(10);
        }
    }
}