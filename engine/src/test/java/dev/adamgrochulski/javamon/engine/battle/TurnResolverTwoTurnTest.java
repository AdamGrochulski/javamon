package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnResolverTwoTurnTest {

    private static final Rng ROLL_100 = (min, max) -> 100;   // trafia, brak krytyka

    private static final Move SOLAR_BEAM = new Move("Solar Beam", Type.GRASS, MoveCategory.SPECIAL,
            120, 100, 10, 0, List.of(), null, Move.TwoTurn.CHARGE);
    private static final Move HYPER_BEAM = new Move("Hyper Beam", Type.NORMAL, MoveCategory.SPECIAL,
            150, 100, 5, 0, List.of(), null, Move.TwoTurn.RECHARGE);

    private static BattlePokemon poke(Move move) {
        return new BattlePokemon("A", new Stats(100, 100, 100, 100, 100, 100), Type.NORMAL, null, 50, List.of(move));
    }

    private static BattlePokemon sturdy() {
        return new BattlePokemon("D", new Stats(250, 100, 250, 250, 250, 100), Type.NORMAL, null, 50, List.of(HYPER_BEAM));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    private static boolean has(List<BattleEvent> ev, Class<?> type) {
        return ev.stream().anyMatch(type::isInstance);
    }

    @Test
    void chargeFirstTurnLoadsWithoutDamage() {
        BattlePokemon user = poke(SOLAR_BEAM);
        BattlePokemon foe = sturdy();
        Battle b = battle(user, foe);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(has(events, BattleEvent.Charging.class));
        assertFalse(has(events, BattleEvent.Damage.class));
        assertTrue(user.isCharging());
        assertTrue(foe.getCurrentHp() == foe.getMaxHp());
    }

    @Test
    void chargeSecondTurnUnleashes() {
        BattlePokemon user = poke(SOLAR_BEAM);
        user.startCharge(SOLAR_BEAM);   // stan po turze 1
        BattlePokemon foe = sturdy();
        Battle b = battle(user, foe);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(has(events, BattleEvent.MoveUsed.class));
        assertTrue(has(events, BattleEvent.Damage.class));
        assertFalse(user.isCharging());
    }

    @Test
    void rechargeSetAfterHyperBeamHits() {
        BattlePokemon user = poke(HYPER_BEAM);
        Battle b = battle(user, sturdy());
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(has(events, BattleEvent.Damage.class));
        assertTrue(user.mustRecharge());
    }

    @Test
    void rechargeSkipsFollowingTurn() {
        BattlePokemon user = poke(HYPER_BEAM);
        user.setMustRecharge();
        Battle b = battle(user, sturdy());
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(has(events, BattleEvent.Recharging.class));
        assertFalse(has(events, BattleEvent.MoveUsed.class));
        assertFalse(user.mustRecharge());
    }
}
