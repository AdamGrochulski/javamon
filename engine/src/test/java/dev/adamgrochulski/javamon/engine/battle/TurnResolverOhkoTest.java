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

class TurnResolverOhkoTest {

    private static final Rng ROLL_100 = (min, max) -> 100;   // accuracy 100 trafia

    private static final Move FISSURE = new Move("Fissure", Type.GROUND, MoveCategory.PHYSICAL, 0, 100, 5, 0,
            List.of(new MoveEffect.OneHitKO()));

    private static BattlePokemon poke(int level) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), Type.NORMAL, null, level,
                List.of(FISSURE));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    private static boolean has(List<BattleEvent> ev, Class<?> type) {
        return ev.stream().anyMatch(type::isInstance);
    }

    @Test
    void ohkoFaintsTargetOnHit() {
        BattlePokemon foe = poke(50);
        Battle b = battle(poke(50), foe);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(foe.isFainted());
        assertTrue(has(events, BattleEvent.OneHitKO.class));
        assertTrue(has(events, BattleEvent.Faint.class));
    }

    @Test
    void ohkoFailsAgainstHigherLevel() {
        BattlePokemon foe = poke(60);
        Battle b = battle(poke(50), foe);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertFalse(foe.isFainted());
        assertTrue(has(events, BattleEvent.MoveFailed.class));
    }
}
