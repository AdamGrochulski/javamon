package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static dev.adamgrochulski.javamon.engine.battle.Player.P2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnResolverFlinchTest {

    // ROLL_100: nextInt zawsze 100 -> chance(100)=true, chance(<100)=false, damage=max roll.
    private static final Rng ROLL_100 = (min, max) -> 100;

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 10, 0);
    private static final Move FAKE_OUT = new Move("Fake Out", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 10, 0,
            List.of(new MoveEffect.Flinch(100)));

    // Base speed steruje kolejnością: wyższa baza -> szybszy po przeliczeniu na level.
    private static BattlePokemon poke(Move move, int baseSpeed) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, baseSpeed), Type.WATER, null, 50, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    @Test
    void fasterFlincherBlocksSlowerTarget() {
        BattlePokemon flincher = poke(FAKE_OUT, 200);
        BattlePokemon target = poke(TACKLE, 100);
        Battle b = battle(flincher, target);

        List<BattleEvent> events = TurnResolver.resolve(b, new MoveAction(0), new MoveAction(0));

        // Cel się wzdrygnął i nie zdążył zaatakować -> flincher bez obrażeń.
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.Flinched f && f.who().player() == P2));
        assertEquals(flincher.getMaxHp(), flincher.getCurrentHp());
    }

    @Test
    void flinchClearedAtEndOfTurn() {
        Battle b = battle(poke(FAKE_OUT, 200), poke(TACKLE, 100));

        TurnResolver.resolve(b, new MoveAction(0), new MoveAction(0));

        // Volatile żyje tylko jedną turę.
        assertFalse(b.side(P1).active().isFlinched());
        assertFalse(b.side(P2).active().isFlinched());
    }

    @Test
    void flinchAfterTargetAlreadyMovedHasNoEffect() {
        // Cel jest szybszy: rusza się PRZED nałożeniem flinch -> jego ruch wchodzi.
        BattlePokemon flincher = poke(FAKE_OUT, 100);
        BattlePokemon fastTarget = poke(TACKLE, 200);
        Battle b = battle(flincher, fastTarget);

        List<BattleEvent> events = TurnResolver.resolve(b, new MoveAction(0), new MoveAction(0));

        assertFalse(events.stream().anyMatch(e -> e instanceof BattleEvent.Flinched));
        assertTrue(flincher.getCurrentHp() < flincher.getMaxHp()); // Tackle celu trafił
    }
}
