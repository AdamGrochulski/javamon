package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.engine.battle.BattleEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Magazyn na potrzeby testów jednostkowych. Trzyma same snapshoty, a te są
 * niezmienne, więc zapis jest realną kopią - tak jak przejście przez Redisa.
 */
class InMemoryBattleStore implements BattleStore {

    private final Map<UUID, BattleSnapshot> saved = new ConcurrentHashMap<>();

    @Override
    public Optional<BattleSnapshot> load(UUID battleId) {
        return Optional.ofNullable(saved.get(battleId));
    }

    @Override
    public void save(BattleSnapshot snapshot) {
        saved.put(snapshot.id(), snapshot);
    }

    private final Map<UUID, List<BattleEvent>> events = new ConcurrentHashMap<>();

    @Override
    public void appendEvents(UUID battleId, List<BattleEvent> batch) {
        events.computeIfAbsent(battleId, id -> new ArrayList<>()).addAll(batch);
    }

    @Override
    public List<BattleEvent> events(UUID battleId) {
        return List.copyOf(events.getOrDefault(battleId, List.of()));
    }

    @Override
    public synchronized <T> T locked(UUID battleId, Supplier<T> action) {
        return action.get();
    }
}
