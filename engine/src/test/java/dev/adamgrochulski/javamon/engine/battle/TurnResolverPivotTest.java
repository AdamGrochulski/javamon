package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TurnResolverPivotTest {

    private static final Rng ROLL_100 = (min, max) -> 100;
    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);
    private static final Move UTURN = new Move("U-turn", Type.BUG, MoveCategory.PHYSICAL, 70, 100, 20, 0,
            List.of(new MoveEffect.ForceSelfSwitch()));

    private static BattlePokemon poke(String name, Type type, Move move) {
        return new BattlePokemon(name, new Stats(100, 100, 100, 100, 100, 100), type, null, 50, List.of(move));
    }

    private static Battle battle(BattleSide p1, BattlePokemon p2) {
        return new Battle(p1, new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    @Test
    void uTurnDamagesThenSwitchesToBench() {
        BattlePokemon user = poke("User", Type.WATER, UTURN);
        BattlePokemon bench = poke("Bench", Type.WATER, TACKLE);
        Battle b = battle(new BattleSide(List.of(user, bench)), poke("Foe", Type.WATER, TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        BattleEvent.Switch sw = assertInstanceOf(BattleEvent.Switch.class, events.get(2));
        assertEquals("User", sw.out().name());
        assertEquals("Bench", sw.in().name());
        assertEquals(1, b.side(P1).getActiveIndex());
    }

    @Test
    void uTurnWithoutBenchJustDamages() {
        BattlePokemon user = poke("User", Type.WATER, UTURN);
        Battle b = battle(new BattleSide(List.of(user)), poke("Foe", Type.WATER, TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, events.size()); // MoveUsed + Damage, brak Switch
        assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        assertEquals(0, b.side(P1).getActiveIndex());
    }

    @Test
    void pivotIntoStealthRockHurtsIncoming() {
        BattlePokemon user = poke("User", Type.WATER, UTURN);
        BattlePokemon bench = poke("Bench", Type.FIRE, TACKLE); // ROCK vs FIRE = 2.0
        Battle b = battle(new BattleSide(List.of(user, bench)), poke("Foe", Type.WATER, TACKLE));
        b.side(P1).addCondition(SideCondition.STEALTH_ROCK);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        BattleEvent.Switch sw = assertInstanceOf(BattleEvent.Switch.class, events.get(2));
        assertEquals("Bench", sw.in().name());
        BattleEvent.HazardHurt hh = assertInstanceOf(BattleEvent.HazardHurt.class, events.get(3));
        assertEquals(40, hh.damage()); // 160 * 2.0 / 8
        assertFalse(b.side(P1).active().isFainted());
    }
}
