package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.engine.battle.Action;
import dev.adamgrochulski.javamon.engine.battle.Battle;
import dev.adamgrochulski.javamon.engine.battle.Player;
import dev.adamgrochulski.javamon.engine.rng.XorShiftRng;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Walka wczytana do pamięci na czas jednej operacji. Nie jest źródłem prawdy:
 * po zmianie stanu idzie z powrotem do magazynu jako BattleSnapshot.
 */
public final class BattleSession {

    private final UUID id;
    private final Battle battle;
    private final XorShiftRng rng;
    private final Participant p1;
    private final Participant p2;
    private final List<MonSnapshot> p1Team;
    private final List<MonSnapshot> p2Team;

    private final Map<Player, Action> pending = new EnumMap<>(Player.class);
    private boolean finished;

    public BattleSession(UUID id, Battle battle, XorShiftRng rng, Participant p1, Participant p2,
                         List<MonSnapshot> p1Team, List<MonSnapshot> p2Team) {
        this.id = id;
        this.battle = battle;
        this.rng = rng;
        this.p1 = p1;
        this.p2 = p2;
        this.p1Team = p1Team;
        this.p2Team = p2Team;
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
        return roster(player).get(teamIndex).speciesId();
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

    public BattleSnapshot toSnapshot() {
        Map<Player, ActionSnapshot> saved = new EnumMap<>(Player.class);
        pending.forEach((player, action) -> saved.put(player, ActionSnapshot.of(action)));

        return new BattleSnapshot(id, p1, p2, p1Team, p2Team,
                rng.state(), battle.state(), saved, finished);
    }

    /** Wołane wyłącznie przy odtwarzaniu z zapisu, zanim sesja wyjdzie z serwisu. */
    void restorePending(Map<Player, ActionSnapshot> saved, boolean wasFinished) {
        saved.forEach((player, action) -> pending.put(player, action.toAction()));
        this.finished = wasFinished;
    }

    private List<MonSnapshot> roster(Player player) {
        return player == Player.P1 ? p1Team : p2Team;
    }
}
