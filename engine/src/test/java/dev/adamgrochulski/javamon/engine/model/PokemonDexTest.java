package dev.adamgrochulski.javamon.engine.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PokemonDexTest {

    private final PokemonDex dex = new PokemonDex();

    @Test
    void ladujeCalyPokedex() {
        assertTrue(dex.size() > 900, "oczekiwano pełnego dexu, było: " + dex.size());
    }

    @Test
    void czytaTypyIStatyGatunku() {
        Species charizard = dex.get("charizard");

        assertEquals("Charizard", charizard.name());
        assertEquals(Type.FIRE, charizard.primary());
        assertEquals(Type.FLYING, charizard.secondary());
        assertEquals(109, charizard.base().specialAttack());
        assertEquals(100, charizard.base().speed());
    }

    @Test
    void gatunekJednotypowyMaDrugiTypNull() {
        assertNull(dex.get("snorlax").secondary());
        assertTrue(dex.get("snorlax").hasType(Type.NORMAL));
    }

    @Test
    void learnsetDecydujeOLegalnosciRuchu() {
        Species charizard = dex.get("charizard");

        assertTrue(charizard.canLearn("Flamethrower"));
        assertFalse(charizard.canLearn("Hydro Pump"));
    }

    @Test
    void nieznanyGatunekRzuca() {
        assertThrows(IllegalArgumentException.class, () -> dex.get("missingno"));
    }

    @Test
    void kazdyRuchWLearnsecieIstniejeWMoveDex() {
        dex.validateLearnsets(new MoveDex());
    }

    @Test
    void learnsetJestNiemutowalny() {
        assertThrows(UnsupportedOperationException.class,
                () -> dex.get("charizard").learnset().add("Hydro Pump"));
    }

    @Test
    void kazdyGatunekDaSieUzycWWalce() {
        for (Species species : dex.all()) {
            assertDoesNotThrow(() -> BattleStats.fromBase(species.base(), 50), species.name());
        }
    }
}