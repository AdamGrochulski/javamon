package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static dev.adamgrochulski.javamon.engine.battle.Player.P2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TurnResolverResolveTest {

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 100, 100, 35, 0);
    // nextInt zawsze 100: accuracy(100) trafia, crit(6) nie, random = 1.0.
    private static final Rng ROLL_100 = (min, max) -> 100;

    private static BattlePokemon poke(Type type, int level, int baseSpeed) {
        Stats base = new Stats(100, 100, 100, 100, 100, baseSpeed);
        return new BattlePokemon("P", base, type, null, level, List.of(TACKLE));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    @Test
    void bothMoveFasterActsFirstAndTurnAdvances() {
        // P1 szybszy (speed 100) niż P2 (50); obaj WATER, ruch NORMAL -> brak STAB, eff 1.0
        Battle b = battle(poke(Type.WATER, 50, 100), poke(Type.WATER, 50, 50));

        List<BattleEvent> events = TurnResolver.resolve(b, new MoveAction(0), new MoveAction(0));

        assertEquals(4, events.size());
        // szybszy P1 pierwszy: MoveUsed(P1), Damage(P2), MoveUsed(P2), Damage(P1)
        assertEquals(P1, assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0)).user().player());
        assertEquals(P2, assertInstanceOf(BattleEvent.Damage.class, events.get(1)).target().player());
        assertEquals(P2, assertInstanceOf(BattleEvent.MoveUsed.class, events.get(2)).user().player());
        assertEquals(P1, assertInstanceOf(BattleEvent.Damage.class, events.get(3)).target().player());
        assertEquals(2, b.getTurn()); // tura++
    }

    @Test
    void koEndsBattleAndSkipsSlowerAction() {
        // P2 wątły (level 1) — szybszy P1 go KO; akcja P2 pominięta, ticki pominięte
        Battle b = battle(poke(Type.WATER, 50, 100), poke(Type.NORMAL, 1, 50));

        List<BattleEvent> events = TurnResolver.resolve(b, new MoveAction(0), new MoveAction(0));

        assertEquals(4, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        assertInstanceOf(BattleEvent.Faint.class, events.get(2));
        assertEquals(P1, assertInstanceOf(BattleEvent.BattleEnd.class, events.get(3)).winner());
        assertEquals(1, b.getTurn()); // early return, tura NIE rośnie
    }

    @Test
    void forfeitEndsBattleImmediately() {
        Battle b = battle(poke(Type.WATER, 50, 100), poke(Type.WATER, 50, 50));

        List<BattleEvent> events = TurnResolver.resolve(b, new ForfeitAction(), new MoveAction(0));

        assertEquals(2, events.size());
        assertEquals(P1, assertInstanceOf(BattleEvent.Forfeit.class, events.get(0)).who());
        assertEquals(P2, assertInstanceOf(BattleEvent.BattleEnd.class, events.get(1)).winner());
        assertEquals(1, b.getTurn());
    }

    @Test
    void statusTicksAfterBothMove() {
        BattlePokemon poisoned = poke(Type.WATER, 50, 100);
        poisoned.applyStatus(StatusCondition.PSN);
        Battle b = battle(poisoned, poke(Type.WATER, 50, 50));

        List<BattleEvent> events = TurnResolver.resolve(b, new MoveAction(0), new MoveAction(0));

        // 4 z ruchów + 1 StatusTick (tylko zatruty P1)
        assertEquals(5, events.size());
        BattleEvent.StatusTick tick = assertInstanceOf(BattleEvent.StatusTick.class, events.get(4));
        assertEquals(P1, tick.who().player());
        assertEquals(StatusCondition.PSN, tick.status());
        assertEquals(2, b.getTurn());
    }
}
