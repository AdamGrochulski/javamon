package dev.adamgrochulski.javamon.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattlePokemonStatusTest {

    // base hp 105, level 100 -> maxHp = (2*105*100)/100 + 100 + 10 = 320.
    // 320/8 = 40 (PSN), 320/16 = 20 (BRN, TOX pierwszy tick).
    private static BattlePokemon poke() {
        Stats base = new Stats(105, 100, 100, 100, 100, 100);
        return new BattlePokemon("Test", base, Type.NORMAL, null, 100);
    }

    @Test
    void applyStatusOnHealthyReturnsTrue() {
        BattlePokemon p = poke();

        assertTrue(p.applyStatus(StatusCondition.PSN));
        assertEquals(StatusCondition.PSN, p.getStatus());
    }

    @Test
    void applyStatusDoesNotOverwriteExisting() {
        BattlePokemon p = poke();
        p.applyStatus(StatusCondition.PSN);

        assertFalse(p.applyStatus(StatusCondition.BRN));
        assertEquals(StatusCondition.PSN, p.getStatus()); // stary status zostaje
    }

    @Test
    void toxicStartsCounterAtOne() {
        BattlePokemon p = poke();
        p.applyStatus(StatusCondition.TOX);

        assertEquals(1, p.getStatusCounter());
    }

    @Test
    void poisonDealsOneEighth() {
        BattlePokemon p = poke();
        p.applyStatus(StatusCondition.PSN);

        assertEquals(40, p.applyEndOfTurnDamage()); // 320/8
        assertEquals(280, p.getCurrentHp());        // faktycznie spadło
    }

    @Test
    void burnDealsOneSixteenth() {
        BattlePokemon p = poke();
        p.applyStatus(StatusCondition.BRN);

        assertEquals(20, p.applyEndOfTurnDamage()); // 320/16
        assertEquals(300, p.getCurrentHp());
    }

    @Test
    void toxicEscalatesEachTick() {
        BattlePokemon p = poke();
        p.applyStatus(StatusCondition.TOX);

        assertEquals(20, p.applyEndOfTurnDamage()); // 320*1/16
        assertEquals(40, p.applyEndOfTurnDamage()); // 320*2/16
        assertEquals(60, p.applyEndOfTurnDamage()); // 320*3/16
        assertEquals(320 - 20 - 40 - 60, p.getCurrentHp()); // 200
    }

    @Test
    void noStatusDealsNoDamage() {
        BattlePokemon p = poke();

        assertEquals(0, p.applyEndOfTurnDamage());
        assertEquals(p.getMaxHp(), p.getCurrentHp()); // HP nietknięte
    }

    @Test
    void damagingStatusAlwaysDealsAtLeastOne() {
        // maxHp = (2*1*1)/100 + 1 + 10 = 11; 11/16 = 0 -> min 1
        Stats base = new Stats(1, 1, 1, 1, 1, 1);
        BattlePokemon frail = new BattlePokemon("Frail", base, Type.NORMAL, null, 1);
        frail.applyStatus(StatusCondition.BRN);

        assertEquals(1, frail.applyEndOfTurnDamage());
    }
}
