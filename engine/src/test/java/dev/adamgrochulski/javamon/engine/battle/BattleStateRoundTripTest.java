package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.damage.TypeChart;
import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.XorShiftRng;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Zapis i odtworzenie stanu walki. Test porównuje nie same pola, tylko przebieg
 * kolejnej tury: gdyby zapis gubił dowolny licznik, obie walki rozeszłyby się.
 */
class BattleStateRoundTripTest {

    private static final Move EMBER =
            new Move("Ember", Type.FIRE, MoveCategory.SPECIAL, 40, 100, 25, 0,
                    List.of(new MoveEffect.InflictStatus(StatusCondition.BRN, MoveEffect.Target.OPPONENT, 100)),
                    null, Move.TwoTurn.NONE);
    private static final Move SOLAR_BEAM =
            new Move("Solar Beam", Type.GRASS, MoveCategory.SPECIAL, 120, 100, 10, 0,
                    List.of(), null, Move.TwoTurn.CHARGE);
    private static final Move SWORDS_DANCE =
            new Move("Swords Dance", Type.NORMAL, MoveCategory.STATUS, 0, 100, 20, 0,
                    List.of(new MoveEffect.StatChange(Stat.ATTACK, 2, MoveEffect.Target.SELF, 100)),
                    null, Move.TwoTurn.NONE);

    private static Battle freshBattle(long seed) {
        return new Battle(
                new BattleSide(List.of(mon("Alfa", Type.WATER, 60), mon("Beta", Type.GRASS, 40))),
                new BattleSide(List.of(mon("Gamma", Type.NORMAL, 50), mon("Delta", Type.ROCK, 30))),
                new XorShiftRng(seed), new TypeChart());
    }

    private static BattlePokemon mon(String name, Type type, int baseSpeed) {
        return new BattlePokemon(name, new Stats(120, 90, 90, 90, 90, baseSpeed), type, null, 50,
                List.of(EMBER, SOLAR_BEAM, SWORDS_DANCE));
    }

    @Test
    void odtworzona_walka_toczy_sie_tak_samo() {
        long seed = 20260922L;
        Battle original = freshBattle(seed);

        // Cztery tury różnych mechanik: status, boost statu, ruch dwuturowy.
        TurnResolver.resolve(original, new MoveAction(0), new MoveAction(2));
        TurnResolver.resolve(original, new MoveAction(2), new MoveAction(1));
        TurnResolver.resolve(original, new MoveAction(1), new MoveAction(0));
        TurnResolver.resolve(original, new MoveAction(0), new MoveAction(2));

        XorShiftRng rngCopy = new XorShiftRng(((XorShiftRng) original.getRng()).state());
        Battle restored = new Battle(
                new BattleSide(List.of(mon("Alfa", Type.WATER, 60), mon("Beta", Type.GRASS, 40))),
                new BattleSide(List.of(mon("Gamma", Type.NORMAL, 50), mon("Delta", Type.ROCK, 30))),
                rngCopy, new TypeChart());
        restored.restore(original.state());

        assertEquals(original.state(), restored.state());

        List<BattleEvent> fromOriginal = TurnResolver.resolve(original, new MoveAction(0), new MoveAction(0));
        List<BattleEvent> fromRestored = TurnResolver.resolve(restored, new MoveAction(0), new MoveAction(0));

        assertEquals(fromOriginal, fromRestored);
        assertEquals(original.state(), restored.state());
    }

    @Test
    void odtworzenie_niesie_pp_i_stopnie_statow() {
        Battle original = freshBattle(7L);
        TurnResolver.resolve(original, new MoveAction(2), new MoveAction(2));

        Battle restored = freshBattle(7L);
        restored.restore(original.state());

        BattlePokemon before = original.side(Player.P1).active();
        BattlePokemon after = restored.side(Player.P1).active();

        assertEquals(before.getStage(Stat.ATTACK), after.getStage(Stat.ATTACK));
        assertEquals(before.ppLeft(2), after.ppLeft(2));
        assertEquals(before.getCurrentHp(), after.getCurrentHp());
    }
}
