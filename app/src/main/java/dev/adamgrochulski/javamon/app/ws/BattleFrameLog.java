package dev.adamgrochulski.javamon.app.ws;

import dev.adamgrochulski.javamon.engine.battle.Player;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Numeracja i bufor ramek, osobno dla każdego odbiorcy. seq należy do protokołu,
 * a nie do stanu walki, więc licznik mieszka tutaj, nie w BattleSession.
 */
@Component
public class BattleFrameLog {

    /** Ile ostatnich ramek trzymamy. Starszy RESUME dostaje pełny stan zamiast przyrostu. */
    private static final int WINDOW = 200;

    private final Map<UUID, Map<Player, Deque<ServerFrame>>> byBattle = new ConcurrentHashMap<>();

    /** Nadaje kolejny numer i zapamiętuje ramkę. */
    public ServerFrame record(UUID battleId, Player player, String type, Object payload) {
        Deque<ServerFrame> log = logOf(battleId, player);
        synchronized (log) {
            long seq = log.isEmpty() ? 1 : log.peekLast().seq() + 1;
            ServerFrame frame = new ServerFrame(type, seq, payload);
            log.addLast(frame);
            if (log.size() > WINDOW) {
                log.removeFirst();
            }
            return frame;
        }
    }

    /** Ramki nowsze niż lastSeq. Pusty Optional = bufor ich już nie ma, trzeba pełnego stanu. */
    public Optional<List<ServerFrame>> since(UUID battleId, Player player, long lastSeq) {
        Deque<ServerFrame> log = logOf(battleId, player);
        synchronized (log) {
            if (log.isEmpty()) {
                return lastSeq == 0 ? Optional.of(List.of()) : Optional.empty();
            }
            if (lastSeq + 1 < log.peekFirst().seq()) {
                return Optional.empty();
            }
            return Optional.of(log.stream().filter(frame -> frame.seq() > lastSeq).toList());
        }
    }

    /** Ostatnia ramka danego typu. Po pełnym resyncu odtwarzamy z niej pytanie o akcję. */
    public Optional<ServerFrame> last(UUID battleId, Player player, String type) {
        Deque<ServerFrame> log = logOf(battleId, player);
        synchronized (log) {
            return log.stream().filter(frame -> frame.type().equals(type)).reduce((first, second) -> second);
        }
    }

    public void forget(UUID battleId) {
        byBattle.remove(battleId);
    }

    private Deque<ServerFrame> logOf(UUID battleId, Player player) {
        return byBattle.computeIfAbsent(battleId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(player, p -> new ArrayDeque<>());
    }
}
