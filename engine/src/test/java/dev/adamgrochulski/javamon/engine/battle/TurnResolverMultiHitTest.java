package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnResolverMultiHitTest {

    // ROLL_100: nextInt zawsze 100. rollHits dla [2,5] -> gałąź else = 5 uderzeń;
    // damage roll = max. Deterministyczne.
    private static final Rng ROLL_100 = (min, max) -> 100;

    private static final Move DOUBLE_KICK = new Move("Double Kick", Type.FIGHTING, MoveCategory.PHYSICAL,
            30, 100, 30, 0, List.of(), new Move.MultiHit(2, 2));
    private static final Move BULLET_SEED = new Move("Bullet Seed", Type.GRASS, MoveCategory.PHYSICAL,
            25, 100, 30, 0, List.of(), new Move.MultiHit(2, 5));

    private static BattlePokemon attacker(Move move) {
        return new BattlePokemon("A", new Stats(100, 100, 100, 100, 100, 100), Type.NORMAL, null, 50, List.of(move));
    }

    private static BattlePokemon sturdy() {
        return new BattlePokemon("D", new Stats(250, 100, 250, 100, 250, 100), Type.NORMAL, null, 50, List.of(DOUBLE_KICK));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    private static long damageEvents(List<BattleEvent> events) {
        return events.stream().filter(e -> e instanceof BattleEvent.Damage).count();
    }

    @Test
    void fixedCountHitsExactlyTwice() {
        Battle b = battle(attacker(DOUBLE_KICK), sturdy());
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, damageEvents(events));
    }

    @Test
    void rangeHitsFiveWithMaxRoll() {
        Battle b = battle(attacker(BULLET_SEED), sturdy());
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(5, damageEvents(events));
    }

    @Test
    void stopsWhenTargetFaints() {
        // Wątły cel: pada od 1. uderzenia -> pętla przerywa, brak kolejnych Damage.
        BattlePokemon frail = new BattlePokemon("F", new Stats(1, 1, 1, 1, 1, 1),
                Type.NORMAL, null, 50, List.of(DOUBLE_KICK));
        Battle b = battle(attacker(DOUBLE_KICK), frail);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(1, damageEvents(events));
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.Faint));
    }
}
