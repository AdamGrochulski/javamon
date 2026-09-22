package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.engine.battle.Action;
import dev.adamgrochulski.javamon.engine.battle.Battle;
import dev.adamgrochulski.javamon.engine.battle.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Jedna walka w toku: stan silnika, przypisanie trenerów do stron, akcje na bieżącą turę. */
public final class BattleSession {

    private final UUID id;
    private final Battle battle;
    private final Participant p1;
    private final Participant p2;
    private final List<String> p1SpeciesIds;
    private final List<String> p2SpeciesIds;

    private final Map<Player, Action> pending = new EnumMap<>(Player.class);
    private boolean finished;

    public BattleSession(UUID id, Battle battle, Participant p1, Participant p2,
                         List<String> p1SpeciesIds, List<String> p2SpeciesIds) {
        this.id = id;
        this.battle = battle;
        this.p1 = p1;
        this.p2 = p2;
        this.p1SpeciesIds = p1SpeciesIds;
        this.p2SpeciesIds = p2SpeciesIds;
    }

    public UUID id() { return id; }
    public Battle battle() { return battle; }

    /** Strona tego trenera albo null, jeśli nie jest uczestnikiem walki. */
    public Player playerOf(UUID trainerId) {
        if (p1.trainerId().equals(trainerId)) return Player.P1;
        if (p2.trainerId().equals(trainerId)) return Player.P2;
        return null;
    }

    public Participant participant(Player player) {
        return player == Player.P1 ? p1 : p2;
    }

    /** Slug z pokedex.json. BattlePokemon zna tylko nazwę, a front potrzebuje id pod sprite'y. */
    public String speciesId(Player player, int teamIndex) {
        return (player == Player.P1 ? p1SpeciesIds : p2SpeciesIds).get(teamIndex);
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

    public boolean isFinished() { return finished; }

    /** Zakończonej walki nie da się już wznowić: kolejne akcje są odrzucane. */
    public void finish() { this.finished = true; }
}