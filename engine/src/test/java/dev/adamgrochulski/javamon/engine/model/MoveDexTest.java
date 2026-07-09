package dev.adamgrochulski.javamon.engine.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveDexTest {

    private MoveDex dex;

    @BeforeEach
    void setUp() {
        dex = new MoveDex();
    }

    @Test
    void loadsFullCatalogFromJson() {
        // pełny dex z danych Showdown — setki ruchów
        assertTrue(dex.size() > 800, "spodziewano się pełnego dexu, było: " + dex.size());
        assertTrue(dex.has("Flamethrower"));
        assertTrue(dex.has("Earthquake"));
        assertFalse(dex.has("Nieistniejący"));
    }

    @Test
    void parsesBasicFields() {
        Move flamethrower = dex.get("Flamethrower");
        assertEquals(Type.FIRE, flamethrower.type());
        assertEquals(MoveCategory.SPECIAL, flamethrower.category());
        assertEquals(90, flamethrower.power());
        assertEquals(100, flamethrower.accuracy());
        assertEquals(15, flamethrower.pp());
    }

    @Test
    void unknownMoveThrows() {
        assertThrows(IllegalArgumentException.class, () -> dex.get("Nieistniejący"));
    }

    @Test
    void parsesGuaranteedStatusEffect() {
        MoveEffect.InflictStatus is = assertInstanceOf(MoveEffect.InflictStatus.class,
                dex.get("Toxic").effects().get(0));
        assertEquals(StatusCondition.TOX, is.status());
        assertEquals(MoveEffect.Target.OPPONENT, is.target());
        assertEquals(100, is.chance());
    }

    @Test
    void parsesSecondaryEffectChance() {
        MoveEffect.InflictStatus is = assertInstanceOf(MoveEffect.InflictStatus.class,
                dex.get("Flamethrower").effects().get(0));
        assertEquals(StatusCondition.BRN, is.status());
        assertEquals(10, is.chance());
    }

    @Test
    void parsesEachSupportedEffectKind() {
        assertInstanceOf(MoveEffect.StatChange.class, dex.get("Swords Dance").effects().get(0));
        assertInstanceOf(MoveEffect.Heal.class, dex.get("Recover").effects().get(0));
        assertInstanceOf(MoveEffect.Hazard.class, dex.get("Stealth Rock").effects().get(0));
        assertInstanceOf(MoveEffect.ForceSelfSwitch.class, dex.get("U-turn").effects().get(0));
        assertInstanceOf(MoveEffect.Recoil.class, dex.get("Brave Bird").effects().get(0));
        assertInstanceOf(MoveEffect.Drain.class, dex.get("Giga Drain").effects().get(0));
        MoveEffect.SetWeather sw = assertInstanceOf(MoveEffect.SetWeather.class, dex.get("Rain Dance").effects().get(0));
        assertEquals(Weather.RAIN, sw.weather());
    }

    @Test
    void flagsUnsupportedMovesAsSimplified() {
        assertTrue(dex.simplifiedCount() > 0);
        assertTrue(dex.isSimplified("Substitute"));     // volatile Substitute — jeszcze niemodelowany
        assertFalse(dex.isSimplified("Flamethrower"));  // w pełni obsługiwany
        assertFalse(dex.isSimplified("Earthquake"));
        assertFalse(dex.isSimplified("Bullet Seed"));   // multi-hit — już obsługiwany
        assertFalse(dex.isSimplified("Solar Beam"));    // charge — już obsługiwany
    }

    @Test
    void parsesTwoTurn() {
        assertEquals(Move.TwoTurn.CHARGE, dex.get("Solar Beam").twoTurn());
        assertEquals(Move.TwoTurn.RECHARGE, dex.get("Hyper Beam").twoTurn());
        assertEquals(Move.TwoTurn.NONE, dex.get("Tackle").twoTurn());
    }

    @Test
    void parsesMultiHit() {
        Move.MultiHit mh = dex.get("Bullet Seed").multiHit();
        assertEquals(2, mh.min());
        assertEquals(5, mh.max());
        Move.MultiHit fixed = dex.get("Double Kick").multiHit();
        assertEquals(2, fixed.min());
        assertEquals(2, fixed.max());
    }

    @Test
    void plainDamageMoveHasNoEffects() {
        assertTrue(dex.get("Tackle").effects().isEmpty());
        assertFalse(dex.isSimplified("Tackle"));
    }
}
