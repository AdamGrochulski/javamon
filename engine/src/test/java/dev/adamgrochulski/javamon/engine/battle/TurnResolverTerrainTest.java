package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnResolverTerrainTest {

    private static final Rng ROLL_100 = (min, max) -> 100;

    private static final Move THUNDERBOLT = new Move("Thunderbolt", Type.ELECTRIC, MoveCategory.SPECIAL, 90, 100, 15, 0);
    private static final Move ELECTRIC_TERRAIN = new Move("Electric Terrain", Type.ELECTRIC, MoveCategory.STATUS, 0, 100, 10, 0,
            List.of(new MoveEffect.SetTerrain(Terrain.ELECTRIC)));
    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);

    private static BattlePokemon poke(Move move, Type type) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), type, null, 50, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    @Test
    void electricTerrainBoostsGroundedElectricMove() {
        BattlePokemon atk = poke(THUNDERBOLT, Type.NORMAL);   // naziemny
        BattlePokemon def = poke(TACKLE, Type.NORMAL);
        TypeChart chart = new TypeChart();

        int none = DamageCalculator.calculate(atk, def, THUNDERBOLT, chart, ROLL_100, Weather.NONE, false, Terrain.NONE).damage();
        int terra = DamageCalculator.calculate(atk, def, THUNDERBOLT, chart, ROLL_100, Weather.NONE, false, Terrain.ELECTRIC).damage();

        assertTrue(terra > none, "teren elektryczny wzmacnia ruch Electric");
    }

    @Test
    void flyingAttackerNotBoostedByTerrain() {
        BattlePokemon flyer = poke(THUNDERBOLT, Type.FLYING);   // nie naziemny
        BattlePokemon def = poke(TACKLE, Type.NORMAL);
        TypeChart chart = new TypeChart();

        int none = DamageCalculator.calculate(flyer, def, THUNDERBOLT, chart, ROLL_100, Weather.NONE, false, Terrain.NONE).damage();
        int terra = DamageCalculator.calculate(flyer, def, THUNDERBOLT, chart, ROLL_100, Weather.NONE, false, Terrain.ELECTRIC).damage();

        assertEquals(none, terra);
    }

    @Test
    void moveSetsTerrain() {
        Battle b = battle(poke(ELECTRIC_TERRAIN, Type.NORMAL), poke(TACKLE, Type.NORMAL));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(Terrain.ELECTRIC, b.getTerrain());
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.TerrainStarted));
    }

    @Test
    void grassyTerrainHealsGroundedThenExpires() {
        BattlePokemon mon = poke(TACKLE, Type.NORMAL);
        mon.takeDamage(40);   // 160 -> 120
        Battle b = battle(mon, poke(TACKLE, Type.NORMAL));
        b.setTerrain(Terrain.GRASSY, 1);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.terrainEndOfTurn(b, events);

        assertEquals(120 + 160 / 16, mon.getCurrentHp());   // +10
        assertEquals(Terrain.NONE, b.getTerrain());
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.TerrainEnded));
    }
}
