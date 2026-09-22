package dev.adamgrochulski.javamon.app.battle;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Ile czasu gracz ma na akcję. Z konfiguracji, żeby test nie czekał trzydziestu sekund. */
@ConfigurationProperties(prefix = "javamon.battle")
public record BattleProperties(Duration actionTimeout) {

    public BattleProperties {
        if (actionTimeout == null) {
            actionTimeout = Duration.ofSeconds(30);
        }
    }
}
