package dev.adamgrochulski.javamon.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.Player.P1;
import static dev.adamgrochulski.javamon.engine.Player.P2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnResolverTicksTest {

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);
    private static final Rng ANY = (min, max) -> 1;

    // base hp 100, level 50 -> maxHp 160; PSN tick = 160/8 = 20.
    private static BattlePokemon poke(int baseSpeed) {
        Stats base = new Stats(100, 100, 100, 100, 100, baseSpeed);
        return new BattlePokemon("P", base, Type.NORMAL, null, 50, List.of(TACKLE));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ANY, new TypeChart());
    }

    @Test
    void poisonedActiveTicksOthersDoNot() {
        BattlePokemon poisoned = poke(100);
        poisoned.applyStatus(StatusCondition.PSN);
        BattlePokemon healthy = poke(50);
        Battle b = battle(poisoned, healthy);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.endOfTurnTicks(b, events);

        assertEquals(1, events.size());
        BattleEvent.StatusTick tick = assertInstanceOf(BattleEvent.StatusTick.class, events.get(0));
        assertEquals(P1, tick.who().player());
        assertEquals(StatusCondition.PSN, tick.status());
        assertEquals(20, tick.damage());
        assertEquals(140, tick.remainingHp());
        assertEquals(140, b.side(P1).active().getCurrentHp());
        assertEquals(160, b.side(P2).active().getCurrentHp()); // zdrowy nietknięty
    }

    @Test
    void noStatusNoEvents() {
        Battle b = battle(poke(100), poke(50));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.endOfTurnTicks(b, events);

        assertTrue(events.isEmpty());
    }

    @Test
    void tickThatKillsEmitsStatusTickThenFaint() {
        BattlePokemon dying = poke(100);
        dying.applyStatus(StatusCondition.PSN);
        dying.takeDamage(dying.getMaxHp() - 10); // zostaje 10 HP, tick 20 dobija
        Battle b = battle(dying, poke(50));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.endOfTurnTicks(b, events);

        assertEquals(2, events.size());
        BattleEvent.StatusTick tick = assertInstanceOf(BattleEvent.StatusTick.class, events.get(0));
        assertEquals(0, tick.remainingHp());
        assertInstanceOf(BattleEvent.Faint.class, events.get(1));
        assertTrue(b.side(P1).active().isFainted());
    }

    @Test
    void bothPoisonedTickInSpeedOrder() {
        BattlePokemon slow = poke(50);
        slow.applyStatus(StatusCondition.PSN);
        BattlePokemon fast = poke(100);
        fast.applyStatus(StatusCondition.PSN);
        Battle b = battle(slow, fast); // P1 wolny, P2 szybki
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.endOfTurnTicks(b, events);

        assertEquals(2, events.size());
        // szybszy (P2) tickuje pierwszy
        assertEquals(P2, ((BattleEvent.StatusTick) events.get(0)).who().player());
        assertEquals(P1, ((BattleEvent.StatusTick) events.get(1)).who().player());
    }
}
