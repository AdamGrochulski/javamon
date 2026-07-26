package dev.adamgrochulski.javamon.app.ws;

import dev.adamgrochulski.javamon.app.auth.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Rejestracja endpointu. Beany infrastrukturalne siedzą w {@link WsInfraConfig},
 * bo klasa konfiguracyjna biorąca handler w konstruktorze nie może jednocześnie
 * dostarczać tego, czego ten handler potrzebuje.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BattleWebSocketHandler handler;
    private final CorsProperties cors;

    WebSocketConfig(BattleWebSocketHandler handler, CorsProperties cors) {
        this.handler = handler;
        this.cors = cors;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Przeglądarka NIE stosuje polityki same-origin do WebSocketów: dowolna
        // strona może otworzyć socket do naszego serwera. Konfiguracja CORS
        // z SecurityConfig tu nie sięga — to jedyne miejsce sprawdzające Origin.
        // Pusta lista = wyłącznie same-origin, więc brak CORS_ORIGINS zamyka,
        // a nie otwiera.
        registry.addHandler(handler, "/ws/battle")
                .setAllowedOrigins(cors.allowedOrigins().toArray(String[]::new));
    }
}
