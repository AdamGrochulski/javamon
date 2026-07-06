package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static dev.adamgrochulski.javamon.engine.battle.Player.P2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnResolverReplacementTest {

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);
    private static final Rng ANY = (min, max) -> 1;

    private static BattlePokemon poke(String name) {
        Stats base = new Stats(100, 100, 100, 100, 100, 100);
        return new BattlePokemon(name, base, Type.NORMAL, null, 50, List.of(TACKLE));
    }

    private static void faint(BattlePokemon mon) {
        mon.takeDamage(mon.getMaxHp());
    }

    private static Battle battle(List<BattlePokemon> p1Team, List<BattlePokemon> p2Team) {
        return new Battle(new BattleSide(p1Team), new BattleSide(p2Team), ANY, new TypeChart());
    }

    // --- needsReplacement / awaitingReplacement ---

    @Test
    void activeFaintedWithLivingBench_needsReplacement() {
        BattlePokemon alpha = poke("Alpha");
        BattlePokemon beta = poke("Beta");
        faint(alpha); // aktywny padł, Beta żyje na ławce
        Battle b = battle(List.of(alpha, beta), List.of(poke("Enemy")));

        assertTrue(b.needsReplacement(P1));
        assertEquals(List.of(P1), b.awaitingReplacement());
    }

    @Test
    void activeAlive_noReplacement() {
        Battle b = battle(List.of(poke("Alpha"), poke("Beta")), List.of(poke("Enemy")));

        assertFalse(b.needsReplacement(P1));
        assertTrue(b.awaitingReplacement().isEmpty());
    }

    @Test
    void wholeTeamFainted_noReplacement_becauseBattleOver() {
        BattlePokemon solo = poke("Solo");
        faint(solo); // cała drużyna martwa
        Battle b = battle(List.of(solo), List.of(poke("Enemy")));

        // koniec walki, nie zejście — inaczej silnik czekałby na zejście bez kandydata
        assertFalse(b.needsReplacement(P1));
        assertTrue(b.isOver());
    }

    @Test
    void doubleFaint_bothAwaitReplacement() {
        BattlePokemon a1 = poke("A1");
        BattlePokemon b1 = poke("B1");
        faint(a1);
        faint(b1);
        Battle b = battle(List.of(a1, poke("A2")), List.of(b1, poke("B2")));

        assertEquals(List.of(P1, P2), b.awaitingReplacement());
    }

    // --- resolveReplacement ---

    @Test
    void resolveReplacement_switchesToBenchAndEmitsSwitch() {
        BattlePokemon alpha = poke("Alpha");
        BattlePokemon beta = poke("Beta");
        faint(alpha);
        Battle b = battle(List.of(alpha, beta), List.of(poke("Enemy")));

        List<BattleEvent> events = TurnResolver.resolveReplacement(b, P1, new SwitchAction(1));

        assertEquals(1, b.side(P1).getActiveIndex());
        assertEquals("Beta", b.side(P1).active().getName());
        assertFalse(b.needsReplacement(P1)); // dług spłacony

        BattleEvent.Switch sw = assertInstanceOf(BattleEvent.Switch.class, events.get(0));
        assertEquals("Alpha", sw.out().name());
        assertEquals("Beta", sw.in().name());
    }

    @Test
    void resolveReplacement_whenNotNeeded_throws() {
        Battle b = battle(List.of(poke("Alpha"), poke("Beta")), List.of(poke("Enemy")));

        // aktywny żyje — nikt nie prosił o zejście; darmowa wymiana zablokowana (anty-cheat)
        assertThrows(IllegalStateException.class,
                () -> TurnResolver.resolveReplacement(b, P1, new SwitchAction(1)));
    }

    // --- guard w resolve ---

    @Test
    void resolve_whenReplacementPending_throws() {
        BattlePokemon alpha = poke("Alpha");
        BattlePokemon beta = poke("Beta");
        faint(alpha);
        Battle b = battle(List.of(alpha, beta), List.of(poke("Enemy")));

        // wisi wybór zejścia — nie wolno rozliczyć normalnej tury
        assertThrows(IllegalStateException.class,
                () -> TurnResolver.resolve(b, new MoveAction(0), new MoveAction(0)));
    }
}
