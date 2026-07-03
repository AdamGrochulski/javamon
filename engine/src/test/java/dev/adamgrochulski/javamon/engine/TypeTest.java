package dev.adamgrochulski.javamon.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeTest {
    @Test
    void hasEighteenTypes() {
        assertEquals(18, Type.values().length);
    }
}