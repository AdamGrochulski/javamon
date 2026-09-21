package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.engine.battle.Action;
import dev.adamgrochulski.javamon.engine.battle.Battle;
import dev.adamgrochulski.javamon.engine.battle.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Jedna walka w toku: stan silnika, przypisanie trenerów do stron, akcje na bieżącą turę. */
public final class BattleSession {

    private final UUID id;
    private final Battle battle;
    private final UUID p1TrainerId;
    private final UUID p2TrainerId;

    private final Map<Player, Action> pending = new EnumMap<>(Player.class);
    private final Map<Player, Long> seq = new EnumMap<>(Player.class);

    public BattleSession(UUID id, Battle battle, UUID p1TrainerId, UUID p2TrainerId) {
        this.id = id;
        this.battle = battle;
        this.p1TrainerId = p1TrainerId;
        this.p2TrainerId = p2TrainerId;
    }

    public UUID id() { return id; }
    public Battle battle() { return battle; }

    /** Strona tego trenera albo null, jeśli nie jest uczestnikiem walki. */
    public Player playerOf(UUID trainerId) {
        if (p1TrainerId.equals(trainerId)) return Player.P1;
        if (p2TrainerId.equals(trainerId)) return Player.P2;
        return null;
    }

    public UUID trainerOf(Player player) {
        return player == Player.P1 ? p1TrainerId : p2TrainerId;
    }

    public boolean hasSubmitted(Player player) { return pending.containsKey(player); }
    public void submit(Player player, Action action) { pending.put(player, action); }
    public boolean bothSubmitted() { return pending.size() == 2; }

    /** Zdejmuje akcje obu graczy i czyści bufor pod następną turę. */
    public Map<Player, Action> takePending() {
        Map<Player, Action> taken = new EnumMap<>(pending);
        pending.clear();
        return taken;
    }

    /** Numer ramki dla tego gracza. Licznik per odbiorca, bo ramki są filtrowane. */
    public long nextSeq(Player player) {
        long next = seq.getOrDefault(player, 0L) + 1;
        seq.put(player, next);
        return next;
    }
}