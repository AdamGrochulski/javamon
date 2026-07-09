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

class TurnResolverProtectTest {

    private static final Rng ROLL_100 = (min, max) -> 100;   // accuracy trafia; chance(<100)=false

    private static final Move PROTECT = new Move("Protect", Type.NORMAL, MoveCategory.STATUS, 0, 100, 10, 4,
            List.of(new MoveEffect.Protect()));
    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);
    private static final Move SWORDS_DANCE = new Move("Swords Dance", Type.NORMAL, MoveCategory.STATUS, 0, 100, 20, 0,
            List.of(new MoveEffect.StatChange(Stat.ATTACK, 2, MoveEffect.Target.SELF, 100)));

    private static BattlePokemon poke(Move move) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), Type.NORMAL, null, 50, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    private static boolean has(List<BattleEvent> ev, Class<?> type) {
        return ev.stream().anyMatch(type::isInstance);
    }

    @Test
    void protectSetsUp() {
        BattlePokemon user = poke(PROTECT);
        Battle b = battle(user, poke(TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(user.isProtected());
        assertTrue(has(events, BattleEvent.ProtectStarted.class));
    }

    @Test
    void protectBlocksIncomingMove() {
        BattlePokemon attacker = poke(TACKLE);
        BattlePokemon defender = poke(TACKLE);
        defender.setProtected();
        Battle b = battle(attacker, defender);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(has(events, BattleEvent.Protected.class));
        assertFalse(has(events, BattleEvent.Damage.class));
        assertEquals(defender.getMaxHp(), defender.getCurrentHp());
    }

    @Test
    void protectDoesNotBlockSelfTargetedMove() {
        BattlePokemon attacker = poke(SWORDS_DANCE);
        BattlePokemon defender = poke(TACKLE);
        defender.setProtected();
        Battle b = battle(attacker, defender);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertFalse(has(events, BattleEvent.Protected.class));
        assertEquals(2, attacker.getStage(Stat.ATTACK));   // Swords Dance na sobie wszedł
    }

    @Test
    void chainedProtectCanFail() {
        BattlePokemon user = poke(PROTECT);
        user.incProtectStreak();   // łańcuch = 1 -> szansa ~33%, ROLL_100 daje porażkę
        Battle b = battle(user, poke(TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertFalse(user.isProtected());
        assertTrue(has(events, BattleEvent.MoveFailed.class));
        assertEquals(0, user.getProtectStreak());   // reset po porażce
    }
}
