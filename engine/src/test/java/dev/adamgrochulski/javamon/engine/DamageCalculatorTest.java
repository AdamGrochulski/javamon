package dev.adamgrochulski.javamon.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageCalculatorTest {

    private TypeChart chart;

    // Fake RNG rozróżniają wywołania po min: crit = nextInt(1,100), random = nextInt(85,100).
    // Kolejność w kalkulatorze: najpierw crit, potem random.

    // Brak crita (roll > 6%) + max random (1.0) — deterministyczny "goły" damage.
    private static final Rng NO_CRIT_MAX_ROLL = (min, max) -> 100;

    // Crit trafia (roll 1 <= 6%), random nadal max (1.0).
    private static final Rng CRIT_MAX_ROLL = (min, max) -> min == 1 ? 1 : 100;

    // Brak crita, minimalny random (85 -> 0.85).
    private static final Rng NO_CRIT_MIN_ROLL = (min, max) -> 85;

    @BeforeEach
    void setUp() {
        chart = new TypeChart();
    }

    // Base 100 we wszystkich statach, level 50 -> derived atk/def = 105.
    private static BattlePokemon poke(Type primary, Type secondary) {
        Stats base = new Stats(100, 100, 100, 100, 100, 100);
        return new BattlePokemon("Test", base, primary, secondary, 50);
    }

    private static Move physical(Type type) {
        return new Move("Tackle", type, MoveCategory.PHYSICAL, 100, 100, 10);
    }

    @Test
    void baseDamageWithoutStabCritOrTypeBonus() {
        // atak WATER, ruch NORMAL (brak STAB), obrońca WATER (NORMAL vs WATER = 1.0)
        BattlePokemon attacker = poke(Type.WATER, null);
        BattlePokemon defender = poke(Type.WATER, null);

        // base = (2*50/5 + 2) * 100 * 105 / 105 / 50 + 2 = 46
        DamageResult result = DamageCalculator.calculate(attacker, defender, physical(Type.NORMAL), chart, NO_CRIT_MAX_ROLL);

        assertEquals(46, result.damage());
        assertFalse(result.crit());
        assertEquals(1.0, result.effectiveness());
        assertFalse(result.noEffect());
    }

    @Test
    void stabMultipliesDamageBy1_5() {
        // atak NORMAL, ruch NORMAL -> STAB 1.5
        BattlePokemon attacker = poke(Type.NORMAL, null);
        BattlePokemon defender = poke(Type.WATER, null);

        DamageResult result = DamageCalculator.calculate(attacker, defender, physical(Type.NORMAL), chart, NO_CRIT_MAX_ROLL);

        assertEquals(69, result.damage()); // (int)(46 * 1.5)
    }

    @Test
    void superEffectiveDoublesDamage() {
        // ruch WATER (atak NORMAL, brak STAB) vs obrońca FIRE -> 2.0
        BattlePokemon attacker = poke(Type.NORMAL, null);
        BattlePokemon defender = poke(Type.FIRE, null);

        Move water = new Move("Bubble", Type.WATER, MoveCategory.PHYSICAL, 100, 100, 10);
        DamageResult result = DamageCalculator.calculate(attacker, defender, water, chart, NO_CRIT_MAX_ROLL);

        assertEquals(92, result.damage()); // (int)(46 * 2.0)
        assertEquals(2.0, result.effectiveness());
        assertFalse(result.noEffect());
    }

    @Test
    void immunityGivesZeroDamageAndNoEffect() {
        // NORMAL vs GHOST = 0.0 (immunity)
        BattlePokemon attacker = poke(Type.WATER, null);
        BattlePokemon defender = poke(Type.GHOST, null);

        DamageResult result = DamageCalculator.calculate(attacker, defender, physical(Type.NORMAL), chart, NO_CRIT_MAX_ROLL);

        assertEquals(0, result.damage());
        assertEquals(0.0, result.effectiveness());
        assertTrue(result.noEffect());
    }

    @Test
    void critMultipliesDamageBy1_5() {
        BattlePokemon attacker = poke(Type.WATER, null);
        BattlePokemon defender = poke(Type.WATER, null);

        DamageResult result = DamageCalculator.calculate(attacker, defender, physical(Type.NORMAL), chart, CRIT_MAX_ROLL);

        assertTrue(result.crit());
        assertEquals(69, result.damage()); // (int)(46 * 1.5)
    }

    @Test
    void minRandomRollLowersDamage() {
        BattlePokemon attacker = poke(Type.WATER, null);
        BattlePokemon defender = poke(Type.WATER, null);

        DamageResult result = DamageCalculator.calculate(attacker, defender, physical(Type.NORMAL), chart, NO_CRIT_MIN_ROLL);

        assertEquals(39, result.damage()); // (int)(46 * 0.85)
        assertFalse(result.crit());
    }

    @Test
    void specialMoveUsesSpecialStats() {
        // przy równych statach fizyczny i specjalny dają ten sam wynik — sprawdza tylko brak wyjątku i sensowny damage
        BattlePokemon attacker = poke(Type.WATER, null);
        BattlePokemon defender = poke(Type.WATER, null);

        Move special = new Move("Water Gun", Type.NORMAL, MoveCategory.SPECIAL, 100, 100, 10);
        DamageResult result = DamageCalculator.calculate(attacker, defender, special, chart, NO_CRIT_MAX_ROLL);

        assertEquals(46, result.damage());
    }

    @Test
    void statusMoveDealsNoDamageButIsNotImmunity() {
        BattlePokemon attacker = poke(Type.WATER, null);
        BattlePokemon defender = poke(Type.WATER, null);

        Move status = new Move("Growl", Type.NORMAL, MoveCategory.STATUS, 0, 100, 10);
        DamageResult result = DamageCalculator.calculate(attacker, defender, status, chart, NO_CRIT_MAX_ROLL);

        assertEquals(0, result.damage());
        assertFalse(result.crit());
        assertEquals(1.0, result.effectiveness()); // 1.0, nie 0.0 — to nie immunity
        assertFalse(result.noEffect());
    }
}
