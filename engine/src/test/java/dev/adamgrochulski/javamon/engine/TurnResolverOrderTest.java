package dev.adamgrochulski.javamon.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.adamgrochulski.javamon.engine.Player.P1;
import static dev.adamgrochulski.javamon.engine.Player.P2;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TurnResolverOrderTest {

    // speed pochodne przy level 100 = 2*baseSpeed + 5 -> różne baseSpeed = różne speed.
    private static BattlePokemon poke(int baseSpeed, int movePriority) {
        Stats base = new Stats(100, 100, 100, 100, 100, baseSpeed);
        Move m = new Move("Move", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 10, movePriority);
        return new BattlePokemon("P", base, Type.NORMAL, null, 100, List.of(m));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2, Rng rng) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), rng, new TypeChart());
    }

    // RNG nieużywany gdy rozstrzyga priority/speed — dowolny.
    private static final Rng ANY = (min, max) -> 1;
    // chance(50): nextInt(1,100) <= 50. 1 -> true (P1), 100 -> false (P2).
    private static final Rng TIE_P1 = (min, max) -> 1;
    private static final Rng TIE_P2 = (min, max) -> 100;

    @Test
    void switchGoesBeforeMove() {
        Battle b = battle(poke(50, 0), poke(50, 0), ANY);

        assertEquals(List.of(P1, P2),
                TurnResolver.order(b, new SwitchAction(0), new MoveAction(0)));
    }

    @Test
    void moveLosesToOpponentSwitch() {
        Battle b = battle(poke(50, 0), poke(50, 0), ANY);

        assertEquals(List.of(P2, P1),
                TurnResolver.order(b, new MoveAction(0), new SwitchAction(0)));
    }

    @Test
    void higherPriorityGoesFirstDespiteLowerSpeed() {
        // P1 wolny (baseSpeed 1) ale priority +1; P2 szybki (baseSpeed 100) priority 0
        Battle b = battle(poke(1, 1), poke(100, 0), ANY);

        assertEquals(List.of(P1, P2),
                TurnResolver.order(b, new MoveAction(0), new MoveAction(0)));
    }

    @Test
    void equalPriorityFasterGoesFirst() {
        Battle b = battle(poke(100, 0), poke(50, 0), ANY);

        assertEquals(List.of(P1, P2),
                TurnResolver.order(b, new MoveAction(0), new MoveAction(0)));
    }

    @Test
    void equalPrioritySlowerGoesSecond() {
        Battle b = battle(poke(50, 0), poke(100, 0), ANY);

        assertEquals(List.of(P2, P1),
                TurnResolver.order(b, new MoveAction(0), new MoveAction(0)));
    }

    @Test
    void speedTieResolvedByRngToP1() {
        Battle b = battle(poke(80, 0), poke(80, 0), TIE_P1);

        assertEquals(List.of(P1, P2),
                TurnResolver.order(b, new MoveAction(0), new MoveAction(0)));
    }

    @Test
    void speedTieResolvedByRngToP2() {
        Battle b = battle(poke(80, 0), poke(80, 0), TIE_P2);

        assertEquals(List.of(P2, P1),
                TurnResolver.order(b, new MoveAction(0), new MoveAction(0)));
    }

    @Test
    void bothSwitchOrderedBySpeed() {
        Battle b = battle(poke(100, 0), poke(50, 0), ANY);

        assertEquals(List.of(P1, P2),
                TurnResolver.order(b, new SwitchAction(0), new SwitchAction(0)));
    }
}
