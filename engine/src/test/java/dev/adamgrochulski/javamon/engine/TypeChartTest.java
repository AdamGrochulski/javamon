package dev.adamgrochulski.javamon.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeChartTest {

    private TypeChart chart;

    @BeforeEach
    void setUp() {
        chart = new TypeChart();
    }

    @Test
    void superEffective() {
        assertEquals(2.0, chart.multiplier(Type.FIRE, Type.GRASS), 0.0001);
    }

    @Test
    void notVeryEffective() {
        assertEquals(0.5, chart.multiplier(Type.FIRE, Type.WATER), 0.0001);
    }

    @Test
    void immune() {
        assertEquals(0.0, chart.multiplier(Type.NORMAL, Type.GHOST), 0.0001);
    }

    @Test
    void defaultIsNeutral() {
        assertEquals(1.0, chart.multiplier(Type.FIRE, Type.NORMAL), 0.0001);
    }
}