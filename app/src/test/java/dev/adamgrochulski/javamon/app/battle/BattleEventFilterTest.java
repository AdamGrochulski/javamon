package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.engine.battle.*;
import dev.adamgrochulski.javamon.engine.damage.TypeChart;
import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.XorShiftRng;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleEventFilterTest {

    private static final PokemonDex POKEDEX = new PokemonDex();
    private static final MoveDex MOVES = new MoveDex();
    private static final TypeChart CHART = new TypeChart();

    private Battle battle;
    private int p2MaxHp;

    @BeforeEach
    void setUp() {
        battle = new Battle(
                new BattleSide(List.of(mon("charizard"))),
                new BattleSide(List.of(mon("blastoise"))),
                new XorShiftRng(1), CHART);
        p2MaxHp = battle.side(Player.P2).getTeam().get(0).getMaxHp();
    }

    @Test
    void wlasne_hp_zostaje_w_punktach() {
        BattleEvent.Damage source = damageOnP2(40, 120);

        BattleEvent.Damage seen = (BattleEvent.Damage) filterFor(Player.P2, source);

        assertEquals(40, seen.damage());
        assertEquals(120, seen.remainingHp());
    }

    @Test
    void hp_przeciwnika_idzie_w_procentach() {
        int half = p2MaxHp / 2;
        BattleEvent.Damage source = damageOnP2(half, half);

        BattleEvent.Damage seen = (BattleEvent.Damage) filterFor(Player.P1, source);

        assertEquals(50, seen.remainingHp());
        assertTrue(seen.damage() <= 50);
    }

    @Test
    void jedno_hp_to_nie_zero() {
        BattleEvent.Damage source = damageOnP2(p2MaxHp - 1, 1);

        BattleEvent.Damage seen = (BattleEvent.Damage) filterFor(Player.P1, source);

        assertEquals(1, seen.remainingHp());
    }

    @Test
    void zero_hp_zostaje_zerem() {
        BattleEvent.Damage source = damageOnP2(p2MaxHp, 0);

        BattleEvent.Damage seen = (BattleEvent.Damage) filterFor(Player.P1, source);

        assertEquals(0, seen.remainingHp());
    }

    @Test
    void event_bez_hp_przechodzi_nietkniety() {
        BattleEvent source = new BattleEvent.MoveUsed(refP2(), "Surf");

        assertSame(source, filterFor(Player.P1, source));
    }

    @Test
    void drain_liczy_sie_z_maxhp_obsianego() {
        BattleEvent.LeechSeedDrain source =
                new BattleEvent.LeechSeedDrain(refP2(), refP1(), p2MaxHp / 8);

        BattleEvent.LeechSeedDrain seen =
                (BattleEvent.LeechSeedDrain) filterFor(Player.P1, source);

        assertEquals(13, seen.amount());
    }

    private BattleEvent filterFor(Player recipient, BattleEvent event) {
        return BattleEventFilter.forPlayer(battle, recipient, List.of(event)).get(0);
    }

    private BattleEvent.Damage damageOnP2(int damage, int remainingHp) {
        return new BattleEvent.Damage(refP2(), damage, remainingHp, false, 1.0);
    }

    private BattleEvent.PokemonRef refP1() {
        return new BattleEvent.PokemonRef(Player.P1, 0, "Charizard");
    }

    private BattleEvent.PokemonRef refP2() {
        return new BattleEvent.PokemonRef(Player.P2, 0, "Blastoise");
    }

    private BattlePokemon mon(String speciesId) {
        Species species = POKEDEX.get(speciesId);
        List<Move> moves = species.learnset().stream().sorted().limit(4).map(MOVES::get).toList();
        return new BattlePokemon(species.name(), species.base(), species.primary(), species.secondary(), 50, moves);
    }
}