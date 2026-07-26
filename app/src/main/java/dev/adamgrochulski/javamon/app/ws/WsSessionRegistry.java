package dev.adamgrochulski.javamon.app.ws;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dwa spojrzenia na te same połączenia: po id sesji (tak identyfikuje je
 * kontener) i po id konta (tak adresuje je reszta aplikacji — silnik nie wie
 * nic o sesjach WebSocketu, zna tylko trenerów).
 */
@Component
public class WsSessionRegistry {

    private final Map<String, WsConnection> bySession = new ConcurrentHashMap<>();
    private final Map<UUID, WsConnection> byTrainer = new ConcurrentHashMap<>();

    void open(WsConnection connection) {
        bySession.put(connection.id(), connection);
    }

    Optional<WsConnection> bySessionId(String sessionId) {
        return Optional.ofNullable(bySession.get(sessionId));
    }

    /** Wiąże połączenie z kontem. Zwraca poprzednie połączenie tego konta, jeśli było. */
    Optional<WsConnection> bind(UUID trainerId, WsConnection connection) {
        return Optional.ofNullable(byTrainer.put(trainerId, connection));
    }

    public Optional<WsConnection> find(UUID trainerId) {
        return Optional.ofNullable(byTrainer.get(trainerId));
    }

    void close(WsConnection connection) {
        bySession.remove(connection.id());
        if (connection.isAuthenticated()) {
            // Dwuargumentowe remove: usuń tylko, jeśli to nadal TO połączenie.
            // Inaczej spóźnione zamknięcie starej sesji wyrzuciłoby z rejestru
            // nowe połączenie, które właśnie ją zastąpiło.
            byTrainer.remove(connection.trainer().id(), connection);
        }
    }
}
