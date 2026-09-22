package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.engine.battle.BattleEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Magazyn trwających walk. Implementacja redisowa jest produkcyjna, pamięciowa testowa. */
public interface BattleStore {

    Optional<BattleSnapshot> load(UUID battleId);

    void save(BattleSnapshot snapshot);

    /** Dopisuje eventy do historii walki. Snapshot stanu ich nie niesie, a replay ich potrzebuje. */
    void appendEvents(UUID battleId, List<BattleEvent> events);

    List<BattleEvent> events(UUID battleId);

    /**
     * Wykonuje akcję na wyłączność dla tej walki. Bez tego dwie akcje wczytają
     * ten sam stan i jedna nadpisze drugą - a stan trzymamy poza JVM-em, więc
     * synchronized niczego tu nie gwarantuje.
     */
    <T> T locked(UUID battleId, Supplier<T> action);
}
