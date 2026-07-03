package dev.adamgrochulski.javamon.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsTest {

    @Test
    void accessorsReturnConstructorValues() {
        // Różne wartości na każdym polu — złapie ewentualne przestawienie pól w konstruktorze.
        Stats stats = new Stats(1, 2, 3, 4, 5, 6);

        assertEquals(1, stats.hp());
        assertEquals(2, stats.attack());
        assertEquals(3, stats.defense());
        assertEquals(4, stats.specialAttack());
        assertEquals(5, stats.specialDefense());
        assertEquals(6, stats.speed());
    }

    @Test
    void rejectsZeroStat() {
        assertThrows(IllegalArgumentException.class,
                () -> new Stats(0, 10, 10, 10, 10, 10));
    }

    @Test
    void rejectsNegativeStat() {
        assertThrows(IllegalArgumentException.class,
                () -> new Stats(10, 10, 10, 10, 10, -1));
    }

    @Test
    void messageNamesFailingStat() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Stats(10, 10, 10, 10, 10, -1));

        assertTrue(ex.getMessage().contains("speed"),
                () -> "komunikat powinien wskazać zły stat, był: " + ex.getMessage());
    }
}
