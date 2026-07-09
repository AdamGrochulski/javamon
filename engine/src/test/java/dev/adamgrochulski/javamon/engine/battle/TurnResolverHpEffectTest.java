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

class TurnResolverHpEffectTest {

    private static final Rng ROLL_100 = (min, max) -> 100;

    // base 100 wszędzie, level 50 -> maxHp = (2*100*50)/100 + 50 + 10 = 160
    private static BattlePokemon poke(Move move) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), Type.WATER, null, 50, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 100, 100, 10, 0);

    @Test
    void recoverHealsSelfByPercentOfMaxHp() {
        Move recover = new Move("Recover", Type.NORMAL, MoveCategory.STATUS, 0, 100, 5, 0,
                List.of(new MoveEffect.Heal(50, MoveEffect.Target.SELF, 100)));
        BattlePokemon user = poke(recover);
        user.takeDamage(100); // 160 -> 60
        Battle b = battle(user, poke(TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, events.size());
        BattleEvent.Healed h = assertInstanceOf(BattleEvent.Healed.class, events.get(1));
        assertEquals(80, h.amount());          // 50% z 160
        assertEquals(140, h.remainingHp());
        assertEquals(140, user.getCurrentHp());
    }

    @Test
    void healCapsAtMaxHp() {
        Move recover = new Move("Recover", Type.NORMAL, MoveCategory.STATUS, 0, 100, 5, 0,
                List.of(new MoveEffect.Heal(50, MoveEffect.Target.SELF, 100)));
        BattlePokemon user = poke(recover);
        user.takeDamage(10); // 160 -> 150
        Battle b = battle(user, poke(TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        BattleEvent.Healed h = assertInstanceOf(BattleEvent.Healed.class, events.get(1));
        assertEquals(10, h.amount());           // dobił tylko do maxHp
        assertEquals(160, user.getCurrentHp());
    }

    @Test
    void recoilDamagesAttackerByPercentOfDamageDealt() {
        Move braveBird = new Move("Brave Bird", Type.NORMAL, MoveCategory.PHYSICAL, 100, 100, 15, 0,
                List.of(new MoveEffect.Recoil(50)));
        BattlePokemon attacker = poke(braveBird);
        Battle b = battle(attacker, poke(TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(3, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        BattleEvent.RecoilDamage r = assertInstanceOf(BattleEvent.RecoilDamage.class, events.get(2));
        assertEquals(23, r.damage());           // 50% z 46
        assertEquals(137, attacker.getCurrentHp()); // 160 - 23
    }

    @Test
    void drainHealsAttackerByPercentOfDamageDealt() {
        Move gigaDrain = new Move("Giga Drain", Type.NORMAL, MoveCategory.PHYSICAL, 100, 100, 10, 0,
                List.of(new MoveEffect.Drain(50)));
        BattlePokemon attacker = poke(gigaDrain);
        attacker.takeDamage(50); // 160 -> 110, żeby leczenie było widoczne
        Battle b = battle(attacker, poke(TACKLE));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(3, events.size());
        BattleEvent.Healed h = assertInstanceOf(BattleEvent.Healed.class, events.get(2));
        assertEquals(23, h.amount());           // 50% z 46
        assertEquals(133, attacker.getCurrentHp()); // 110 + 23
    }
}
