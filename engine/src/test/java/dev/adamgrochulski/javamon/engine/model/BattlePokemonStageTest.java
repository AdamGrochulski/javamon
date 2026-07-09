package dev.adamgrochulski.javamon.engine.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattlePokemonStageTest {

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);

    // base 100 wszędzie, level 50 -> derived atk = (2*100*50)/100 + 5 = 105
    private static BattlePokemon poke() {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100),
                Type.NORMAL, null, 50, List.of(TACKLE));
    }

    @Test
    void stageZeroLeavesStatUnchanged() {
        BattlePokemon p = poke();
        assertEquals(0, p.getStage(Stat.ATTACK));
        assertEquals(105, p.getEffectiveAttack());
    }

    @Test
    void positiveStagesMultiplyByHalfSteps() {
        BattlePokemon p = poke();
        assertEquals(1, p.changeStage(Stat.ATTACK, 1));
        assertEquals(157, p.getEffectiveAttack()); // 105 * 3/2

        assertEquals(1, p.changeStage(Stat.ATTACK, 1));
        assertEquals(210, p.getEffectiveAttack()); // 105 * 4/2
    }

    @Test
    void negativeStageDividesByHalfSteps() {
        BattlePokemon p = poke();
        assertEquals(-1, p.changeStage(Stat.ATTACK, -1));
        assertEquals(70, p.getEffectiveAttack()); // 105 * 2/3
    }

    @Test
    void stagesClampAtPlusMinusSix() {
        BattlePokemon p = poke();
        assertEquals(6, p.changeStage(Stat.SPEED, 10));   // obcięte do +6
        assertEquals(6, p.getStage(Stat.SPEED));
        assertEquals(0, p.changeStage(Stat.SPEED, 3));    // już przy limicie -> 0 zmiany

        assertEquals(-6, p.changeStage(Stat.DEFENSE, -9)); // obcięte do -6
        assertEquals(-6, p.getStage(Stat.DEFENSE));
        assertEquals(0, p.changeStage(Stat.DEFENSE, -1));
    }
}
