package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.engine.battle.Battle;
import dev.adamgrochulski.javamon.engine.battle.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cała walka w jednym dokumencie: kto gra, czym gra, stan silnika, stan RNG
 * i akcje przysłane na bieżącą turę, ale jeszcze nierozliczone.
 */
public record BattleSnapshot(UUID id, Participant p1, Participant p2,
                             List<MonSnapshot> p1Team, List<MonSnapshot> p2Team,
                             long rngState, Battle.State state,
                             Map<Player, ActionSnapshot> pending, boolean finished) {
}
