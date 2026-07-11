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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnResolverScreenTest {

    // ROLL_100: brak krytyka (chance(6)=false), random = max. Deterministyczne.
    private static final Rng ROLL_100 = (min, max) -> 100;

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 100, 100, 35, 0);
    private static final Move REFLECT = new Move("Reflect", Type.PSYCHIC, MoveCategory.STATUS, 0, 100, 20, 0,
            List.of(new MoveEffect.SetScreen(SideCondition.REFLECT)));

    private static BattlePokemon poke(Move move) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), Type.NORMAL, null, 50, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    @Test
    void reflectHalvesPhysicalDamage() {
        BattlePokemon atk = poke(TACKLE);
        BattlePokemon def = poke(TACKLE);
        TypeChart chart = new TypeChart();

        int normal = DamageCalculator.calculate(atk, def, TACKLE, chart, ROLL_100, Weather.NONE, false).damage();
        int screened = DamageCalculator.calculate(atk, def, TACKLE, chart, ROLL_100, Weather.NONE, true).damage();

        assertEquals(normal / 2, screened);
    }

    @Test
    void moveSetsScreenAndReducesIncomingDamage() {
        Battle b = battle(poke(REFLECT), poke(TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        // P1 stawia Reflect na swojej stronie.
        TurnResolver.executeMove(b, P1, new MoveAction(0), events);
        assertTrue(b.side(P1).hasCondition(SideCondition.REFLECT));
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.ScreenSet));

        // P2 uderza fizycznie w P1 — obrażenia połowione względem braku ekranu.
        List<BattleEvent> atkEvents = new ArrayList<>();
        TurnResolver.executeMove(b, P2, new MoveAction(0), atkEvents);
        BattleEvent.Damage dmg = (BattleEvent.Damage) atkEvents.stream()
                .filter(e -> e instanceof BattleEvent.Damage).findFirst().orElseThrow();

        BattlePokemon freshDef = poke(TACKLE);
        int unscreened = DamageCalculator.calculate(b.side(P2).active(), freshDef, TACKLE,
                b.getChart(), ROLL_100, Weather.NONE, false).damage();
        assertEquals(unscreened / 2, dmg.damage());
    }

    @Test
    void screenExpiresAfterDuration() {
        BattleSide side = new BattleSide(List.of(poke(TACKLE)));
        side.addTimedCondition(SideCondition.REFLECT, 1);
        Battle b = new Battle(side, new BattleSide(List.of(poke(TACKLE))), ROLL_100, new TypeChart());
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.tickScreens(b, events);

        assertFalse(b.side(P1).hasCondition(SideCondition.REFLECT));
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.ScreenFaded));
    }

    @Test
    void auroraVeilNeedsSnow() {
        Move auroraVeil = new Move("Aurora Veil", Type.ICE, MoveCategory.STATUS, 0, 100, 20, 0,
                List.of(new MoveEffect.SetScreen(SideCondition.AURORA_VEIL)));
        Battle noSnow = battle(poke(auroraVeil), poke(TACKLE));
        TurnResolver.executeMove(noSnow, P1, new MoveAction(0), new ArrayList<>());
        assertFalse(noSnow.side(P1).hasCondition(SideCondition.AURORA_VEIL));

        Battle snow = battle(poke(auroraVeil), poke(TACKLE));
        snow.setWeather(Weather.SNOW, 5);
        TurnResolver.executeMove(snow, P1, new MoveAction(0), new ArrayList<>());
        assertTrue(snow.side(P1).hasCondition(SideCondition.AURORA_VEIL));
    }
}
