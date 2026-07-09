package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static dev.adamgrochulski.javamon.engine.battle.Player.P2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnResolverHazardTest {

    private static final Rng ANY = (min, max) -> 1;
    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);
    private static final Move STEALTH_ROCK = new Move("Stealth Rock", Type.ROCK, MoveCategory.STATUS, 0, 100, 20, 0,
            List.of(new MoveEffect.Hazard(SideCondition.STEALTH_ROCK)));

    // maxHp przy base 100 / level 50 = 160
    private static BattlePokemon poke(Type type, Move move) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), type, null, 50, List.of(move));
    }

    @Test
    void stealthRockMoveSetsHazardOnOpponentSide() {
        Battle b = new Battle(new BattleSide(List.of(poke(Type.WATER, STEALTH_ROCK))),
                new BattleSide(List.of(poke(Type.WATER, TACKLE))), ANY, new TypeChart());
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(b.side(P2).hasCondition(SideCondition.STEALTH_ROCK));
        BattleEvent.HazardSet hs = assertInstanceOf(BattleEvent.HazardSet.class, events.get(1));
        assertEquals(P2, hs.side());
        assertEquals(SideCondition.STEALTH_ROCK, hs.condition());
    }

    @Test
    void switchInTakesNeutralStealthRockDamage() {
        BattlePokemon alpha = poke(Type.WATER, TACKLE);
        BattlePokemon beta = poke(Type.WATER, TACKLE); // ROCK vs WATER = 1.0
        Battle b = new Battle(new BattleSide(List.of(alpha, beta)),
                new BattleSide(List.of(poke(Type.WATER, TACKLE))), ANY, new TypeChart());
        b.side(P1).addCondition(SideCondition.STEALTH_ROCK);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeSwitch(b, P1, new SwitchAction(1), events);

        assertInstanceOf(BattleEvent.Switch.class, events.get(0));
        BattleEvent.HazardHurt hh = assertInstanceOf(BattleEvent.HazardHurt.class, events.get(1));
        assertEquals(20, hh.damage());        // 160 * 1.0 / 8
        assertEquals(140, beta.getCurrentHp());
    }

    @Test
    void weaknessToRockDoublesStealthRockDamage() {
        BattlePokemon alpha = poke(Type.WATER, TACKLE);
        BattlePokemon beta = poke(Type.FIRE, TACKLE); // ROCK vs FIRE = 2.0
        Battle b = new Battle(new BattleSide(List.of(alpha, beta)),
                new BattleSide(List.of(poke(Type.WATER, TACKLE))), ANY, new TypeChart());
        b.side(P1).addCondition(SideCondition.STEALTH_ROCK);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeSwitch(b, P1, new SwitchAction(1), events);

        BattleEvent.HazardHurt hh = assertInstanceOf(BattleEvent.HazardHurt.class, events.get(1));
        assertEquals(40, hh.damage());        // 160 * 2.0 / 8
    }

    @Test
    void replacementAfterFaintAlsoTriggersHazard() {
        BattlePokemon alpha = poke(Type.WATER, TACKLE);
        BattlePokemon beta = poke(Type.FIRE, TACKLE);
        alpha.takeDamage(alpha.getMaxHp()); // aktywny pada -> replacement
        Battle b = new Battle(new BattleSide(List.of(alpha, beta)),
                new BattleSide(List.of(poke(Type.WATER, TACKLE))), ANY, new TypeChart());
        b.side(P1).addCondition(SideCondition.STEALTH_ROCK);

        List<BattleEvent> events = TurnResolver.resolveReplacement(b, P1, new SwitchAction(1));

        assertInstanceOf(BattleEvent.Switch.class, events.get(0));
        BattleEvent.HazardHurt hh = assertInstanceOf(BattleEvent.HazardHurt.class, events.get(1));
        assertEquals(40, hh.damage());
    }
}
