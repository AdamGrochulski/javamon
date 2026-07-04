package dev.adamgrochulski.javamon.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.Player.P1;
import static dev.adamgrochulski.javamon.engine.Player.P2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnResolverSwitchTest {

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);
    private static final Rng ANY = (min, max) -> 1;

    private static BattlePokemon poke(String name) {
        Stats base = new Stats(100, 100, 100, 100, 100, 100);
        return new BattlePokemon(name, base, Type.NORMAL, null, 50, List.of(TACKLE));
    }

    private static Battle battleP1Team(List<BattlePokemon> p1Team) {
        return new Battle(new BattleSide(p1Team), new BattleSide(List.of(poke("Enemy"))), ANY, new TypeChart());
    }

    @Test
    void switchChangesActiveAndEmitsSwitchEvent() {
        BattlePokemon alpha = poke("Alpha");
        BattlePokemon beta = poke("Beta");
        Battle b = battleP1Team(List.of(alpha, beta));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeSwitch(b, P1, new SwitchAction(1), events);

        // stan: aktywny to teraz Beta (index 1)
        assertEquals(1, b.side(P1).getActiveIndex());
        assertEquals("Beta", b.side(P1).active().getName());

        // event: out = Alpha(0), in = Beta(1), oba po stronie P1
        assertEquals(1, events.size());
        BattleEvent.Switch sw = assertInstanceOf(BattleEvent.Switch.class, events.get(0));
        assertEquals(P1, sw.out().player());
        assertEquals(0, sw.out().teamIndex());
        assertEquals("Alpha", sw.out().name());
        assertEquals(1, sw.in().teamIndex());
        assertEquals("Beta", sw.in().name());
    }

    @Test
    void switchToFaintedPropagatesException() {
        BattlePokemon alpha = poke("Alpha");
        BattlePokemon beta = poke("Beta");
        beta.takeDamage(beta.getMaxHp()); // Beta pada
        Battle b = battleP1Team(List.of(alpha, beta));
        List<BattleEvent> events = new ArrayList<>();

        // switchTo broni niezmiennika — nie wejdziesz na trupa
        assertThrows(IllegalArgumentException.class,
                () -> TurnResolver.executeSwitch(b, P1, new SwitchAction(1), events));
    }
}
