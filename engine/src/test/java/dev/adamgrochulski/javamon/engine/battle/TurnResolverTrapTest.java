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

class TurnResolverTrapTest {

    private static final Rng ROLL_100 = (min, max) -> 100;

    private static final Move FIRE_SPIN = new Move("Fire Spin", Type.FIRE, MoveCategory.SPECIAL, 35, 100, 15, 0,
            List.of(new MoveEffect.Trap(MoveEffect.Target.OPPONENT, 100)));
    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);

    // base 100 / lvl 50 -> maxHp 160, chip 1/8 = 20.
    private static BattlePokemon poke(Move move) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), Type.NORMAL, null, 50, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    @Test
    void moveTrapsTarget() {
        BattlePokemon foe = poke(TACKLE);
        Battle b = battle(poke(FIRE_SPIN), foe);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(foe.isTrapped());
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.Trapped));
    }

    @Test
    void trapChipsEachTurnThenEnds() {
        BattlePokemon trapped = poke(TACKLE);
        trapped.trap(2);
        Battle b = battle(trapped, poke(TACKLE));

        List<BattleEvent> first = new ArrayList<>();
        TurnResolver.trapEndOfTurn(b, first);
        assertEquals(160 - 20, trapped.getCurrentHp());
        assertTrue(trapped.isTrapped());
        assertTrue(first.stream().anyMatch(e -> e instanceof BattleEvent.TrapHurt));

        List<BattleEvent> second = new ArrayList<>();
        TurnResolver.trapEndOfTurn(b, second);
        assertEquals(160 - 40, trapped.getCurrentHp());
        assertFalse(trapped.isTrapped());
        assertTrue(second.stream().anyMatch(e -> e instanceof BattleEvent.TrapEnded));
    }

    @Test
    void untrappedTakesNoChip() {
        BattlePokemon free = poke(TACKLE);
        Battle b = new Battle(new BattleSide(List.of(free)), new BattleSide(List.of(poke(TACKLE))), ROLL_100, new TypeChart());

        TurnResolver.trapEndOfTurn(b, new ArrayList<>());

        assertEquals(free.getMaxHp(), free.getCurrentHp());
    }
}
