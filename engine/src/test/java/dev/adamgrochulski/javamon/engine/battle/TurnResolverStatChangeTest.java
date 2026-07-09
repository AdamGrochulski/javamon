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

class TurnResolverStatChangeTest {

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 100, 100, 10, 0);
    private static final Rng ROLL_100 = (min, max) -> 100;

    private static final Move SWORDS_DANCE = new Move("Swords Dance", Type.NORMAL, MoveCategory.STATUS, 0, 100, 20, 0,
            List.of(new MoveEffect.StatChange(Stat.ATTACK, 2, MoveEffect.Target.SELF, 100)));
    private static final Move GROWL = new Move("Growl", Type.NORMAL, MoveCategory.STATUS, 0, 100, 40, 0,
            List.of(new MoveEffect.StatChange(Stat.ATTACK, -1, MoveEffect.Target.OPPONENT, 100)));

    private static BattlePokemon poke(Move move) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), Type.WATER, null, 50, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    @Test
    void selfBoostRaisesOwnStage() {
        BattlePokemon user = poke(SWORDS_DANCE);
        Battle b = battle(user, poke(TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, user.getStage(Stat.ATTACK));
        assertEquals(2, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        BattleEvent.StatStageChanged sc = assertInstanceOf(BattleEvent.StatStageChanged.class, events.get(1));
        assertEquals(P1, sc.who().player());
        assertEquals(Stat.ATTACK, sc.stat());
        assertEquals(2, sc.delta());
        assertEquals(2, sc.newStage());
    }

    @Test
    void debuffLowersOpponentStage() {
        BattlePokemon target = poke(TACKLE);
        Battle b = battle(poke(GROWL), target);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(-1, target.getStage(Stat.ATTACK));
        BattleEvent.StatStageChanged sc = assertInstanceOf(BattleEvent.StatStageChanged.class, events.get(1));
        assertEquals(P2, sc.who().player());
        assertEquals(-1, sc.delta());
    }

    @Test
    void boostAtCapEmitsNoEvent() {
        BattlePokemon user = poke(SWORDS_DANCE);
        user.changeStage(Stat.ATTACK, 6); // już maks
        Battle b = battle(user, poke(TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(6, user.getStage(Stat.ATTACK)); // bez zmiany
        assertEquals(1, events.size());              // tylko MoveUsed
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
    }

    @Test
    void attackBoostIncreasesDamage() {
        // +2 atk (105 -> 210) podwaja bazę; damage rośnie z 46 do 90
        BattlePokemon attacker = poke(TACKLE);
        attacker.changeStage(Stat.ATTACK, 2);
        Battle b = battle(attacker, poke(TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        BattleEvent.Damage dmg = assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        assertEquals(90, dmg.damage());
    }
}
