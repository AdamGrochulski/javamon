package dev.adamgrochulski.javamon.app.battle;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Magazyn trwających walk. Implementacja redisowa jest produkcyjna, pamięciowa testowa. */
public interface BattleStore {

    Optional<BattleSnapshot> load(UUID battleId);

    void save(BattleSnapshot snapshot);

    /**
     * Wykonuje akcję na wyłączność dla tej walki. Bez tego dwie akcje wczytają
     * ten sam stan i jedna nadpisze drugą - a stan trzymamy poza JVM-em, więc
     * synchronized niczego tu nie gwarantuje.
     */
    <T> T locked(UUID battleId, Supplier<T> action);
}
