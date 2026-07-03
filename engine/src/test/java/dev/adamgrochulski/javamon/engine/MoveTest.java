package dev.adamgrochulski.javamon.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveTest {

    @Test
    void accessorsReturnConstructorValues() {
        Move flamethrower = new Move("Flamethrower", Type.FIRE, MoveCategory.SPECIAL, 90, 100, 15);

        assertEquals("Flamethrower", flamethrower.name());
        assertEquals(Type.FIRE, flamethrower.type());
        assertEquals(MoveCategory.SPECIAL, flamethrower.category());
        assertEquals(90, flamethrower.power());
        assertEquals(100, flamethrower.accuracy());
        assertEquals(15, flamethrower.pp());
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Move("  ", Type.FIRE, MoveCategory.SPECIAL, 90, 100, 15));
    }

    @Test
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Move(null, Type.FIRE, MoveCategory.SPECIAL, 90, 100, 15));
    }

    @Test
    void rejectsNegativePower() {
        assertThrows(IllegalArgumentException.class,
                () -> new Move("Bad", Type.FIRE, MoveCategory.SPECIAL, -1, 100, 15));
    }

    @Test
    void rejectsAccuracyOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new Move("Bad", Type.FIRE, MoveCategory.SPECIAL, 90, 0, 15));
        assertThrows(IllegalArgumentException.class,
                () -> new Move("Bad", Type.FIRE, MoveCategory.SPECIAL, 90, 101, 15));
    }

    @Test
    void rejectsNonPositivePp() {
        assertThrows(IllegalArgumentException.class,
                () -> new Move("Bad", Type.FIRE, MoveCategory.SPECIAL, 90, 100, 0));
    }

    @Test
    void rejectsStatusMoveWithPower() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Move("Toxic", Type.POISON, MoveCategory.STATUS, 40, 90, 10));

        assertTrue(ex.getMessage().contains("STATUS"),
                () -> "komunikat powinien wskazać regułę STATUS, był: " + ex.getMessage());
    }

    @Test
    void allowsStatusMoveWithZeroPower() {
        assertDoesNotThrow(
                () -> new Move("Toxic", Type.POISON, MoveCategory.STATUS, 0, 90, 10));
    }
}
