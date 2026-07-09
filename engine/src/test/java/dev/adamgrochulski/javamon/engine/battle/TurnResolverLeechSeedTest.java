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

class TurnResolverLeechSeedTest {

    private static final Rng ROLL_100 = (min, max) -> 100;

    // Accuracy 100 (nie 90 jak w grze) dla deterministycznego trafienia w teście.
    private static final Move LEECH_SEED = new Move("Leech Seed", Type.GRASS, MoveCategory.STATUS, 0, 100, 10, 0,
            List.of(new MoveEffect.LeechSeed()));
    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);

    // base 100 / lvl 50 -> maxHp 160, drain 1/8 = 20.
    private static BattlePokemon poke(Move move, Type type) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), type, null, 50, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    @Test
    void moveSeedsNonGrassTarget() {
        BattlePokemon foe = poke(TACKLE, Type.NORMAL);
        Battle b = battle(poke(LEECH_SEED, Type.GRASS), foe);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertTrue(foe.isLeechSeeded());
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.Seeded));
    }

    @Test
    void grassTypeImmuneToSeed() {
        BattlePokemon grassFoe = poke(TACKLE, Type.GRASS);
        Battle b = battle(poke(LEECH_SEED, Type.GRASS), grassFoe);

        TurnResolver.executeMove(b, P1, new MoveAction(0), new ArrayList<>());

        assertFalse(grassFoe.isLeechSeeded());
    }

    @Test
    void drainHurtsSeededAndHealsOpponent() {
        BattlePokemon seeder = poke(LEECH_SEED, Type.GRASS);
        BattlePokemon seeded = poke(TACKLE, Type.NORMAL);
        seeded.takeDamage(40);   // 160 -> 120, żeby leczenie miało efekt
        seeder.takeDamage(50);   // 160 -> 110
        seeded.seed();
        Battle b = battle(seeder, seeded);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.leechSeedEndOfTurn(b, events);

        assertEquals(120 - 20, seeded.getCurrentHp());   // stracił 1/8 = 20
        assertEquals(110 + 20, seeder.getCurrentHp());   // odzyskał tyle samo
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.LeechSeedDrain));
    }
}
