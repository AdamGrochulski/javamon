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

class TurnResolverConfusionTest {

    // LOW: nextInt=1 -> chance(x)=true (1<=x). HIGH: nextInt=100 -> chance(<100)=false.
    private static final Rng LOW = (min, max) -> 1;
    private static final Rng HIGH = (min, max) -> 100;

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);
    private static final Move CONFUSE_RAY = new Move("Confuse Ray", Type.GHOST, MoveCategory.STATUS, 0, 100, 10, 0,
            List.of(new MoveEffect.Confuse(MoveEffect.Target.OPPONENT, 100)));

    private static BattlePokemon poke(Move move) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), Type.NORMAL, null, 50, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2, Rng rng) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), rng, new TypeChart());
    }

    private static boolean has(List<BattleEvent> ev, Class<?> type) {
        return ev.stream().anyMatch(type::isInstance);
    }

    @Test
    void moveInflictsConfusion() {
        Battle b = battle(poke(CONFUSE_RAY), poke(TACKLE), HIGH);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(b.side(Player.P2).active().isConfused());
        assertTrue(has(events, BattleEvent.ConfusionStarted.class));
    }

    @Test
    void confusedHitsSelfAndSkipsMove() {
        BattlePokemon confused = poke(TACKLE);
        confused.confuse(4);
        BattlePokemon foe = poke(TACKLE);
        Battle b = battle(confused, foe, LOW);   // chance(33)=true -> uderza siebie
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(has(events, BattleEvent.ConfusionHit.class));
        assertFalse(has(events, BattleEvent.MoveUsed.class));       // ruch nie wyszedł
        assertTrue(confused.getCurrentHp() < confused.getMaxHp());  // ranił siebie
        assertTrue(foe.getCurrentHp() == foe.getMaxHp());           // przeciwnik nietknięty
    }

    @Test
    void confusedButPassesRollMovesNormally() {
        BattlePokemon confused = poke(TACKLE);
        confused.confuse(4);
        Battle b = battle(confused, poke(TACKLE), HIGH);   // chance(33)=false -> rusza się
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(has(events, BattleEvent.MoveUsed.class));
        assertFalse(has(events, BattleEvent.ConfusionHit.class));
    }

    @Test
    void confusionEndsAfterCounterAndMonMoves() {
        BattlePokemon confused = poke(TACKLE);
        confused.confuse(1);   // ostatnia tura zmieszania
        Battle b = battle(confused, poke(TACKLE), HIGH);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(has(events, BattleEvent.ConfusionEnded.class));
        assertTrue(has(events, BattleEvent.MoveUsed.class));   // oprzytomniał i uderzył
        assertFalse(confused.isConfused());
    }
}
