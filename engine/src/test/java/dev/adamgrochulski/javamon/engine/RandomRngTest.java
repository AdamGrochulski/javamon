package dev.adamgrochulski.javamon.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomRngTest {

    @Test
    void singleValueRangeReturnsThatValue() {
        Rng rng = new RandomRng(42);
        for (int i = 0; i < 100; i++) {
            assertEquals(5, rng.nextInt(5, 5));
        }
    }

    @Test
    void resultAlwaysWithinInclusiveRange() {
        Rng rng = new RandomRng(1);
        for (int i = 0; i < 10_000; i++) {
            int v = rng.nextInt(1, 6);
            assertTrue(v >= 1 && v <= 6, () -> "poza zakresem: " + v);
        }
    }

    @Test
    void sameSeedProducesSameSequence() {
        RandomRng a = new RandomRng(123);
        RandomRng b = new RandomRng(123);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.nextInt(1, 1000), b.nextInt(1, 1000));
        }
    }

    @Test
    void chanceBoundariesAreDeterministic() {
        Rng rng = new RandomRng(7);
        // chance(100) zawsze true, chance(0) zawsze false — niezależnie od losowania.
        for (int i = 0; i < 100; i++) {
            assertTrue(rng.chance(100));
            assertFalse(rng.chance(0));
        }
    }

    @Test
    void chanceComparesRollAgainstPercent() {
        // Fake RNG lambdą (Rng to interfejs funkcyjny): nextInt zawsze 50.
        // Testuje logikę default-metody chance w izolacji, bez prawdziwego losowania.
        Rng fixed50 = (min, max) -> 50;
        assertTrue(fixed50.chance(50));   // 50 <= 50
        assertTrue(fixed50.chance(51));   // 50 <= 51
        assertFalse(fixed50.chance(49));  // 50 <= 49 -> false
    }
}
