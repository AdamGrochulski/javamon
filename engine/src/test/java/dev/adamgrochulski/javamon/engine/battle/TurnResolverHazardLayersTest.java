package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TurnResolverHazardLayersTest {

    private static final Rng ROLL_100 = (min, max) -> 100;
    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);

    // base 100 / lvl 50 -> maxHp 160.
    private static BattlePokemon poke(Type type) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), type, null, 50, List.of(TACKLE));
    }

    // Side z aktywnym mon0 (NORMAL) i zmiennikiem mon1 danego typu.
    private static Battle battleSwitchInto(Type benchType) {
        BattleSide p1 = new BattleSide(List.of(poke(Type.NORMAL), poke(benchType)));
        BattleSide p2 = new BattleSide(List.of(poke(Type.NORMAL)));
        return new Battle(p1, p2, ROLL_100, new TypeChart());
    }

    private static List<BattleEvent> switchIn(Battle b) {
        List<BattleEvent> events = new ArrayList<>();
        TurnResolver.executeSwitch(b, P1, new SwitchAction(1), events);
        return events;
    }

    @Test
    void spikesTwoLayersHitForOneSixth() {
        Battle b = battleSwitchInto(Type.NORMAL);
        b.side(P1).addCondition(SideCondition.SPIKES);
        b.side(P1).addCondition(SideCondition.SPIKES);

        switchIn(b);

        BattlePokemon in = b.side(P1).active();
        assertEquals(160 - 160 / 6, in.getCurrentHp());
    }

    @Test
    void flyingImmuneToSpikes() {
        Battle b = battleSwitchInto(Type.FLYING);
        b.side(P1).addCondition(SideCondition.SPIKES);

        switchIn(b);

        assertEquals(160, b.side(P1).active().getCurrentHp());
    }

    @Test
    void toxicSpikesPoisonGrounded() {
        Battle oneLayer = battleSwitchInto(Type.NORMAL);
        oneLayer.side(P1).addCondition(SideCondition.TOXIC_SPIKES);
        switchIn(oneLayer);
        assertEquals(StatusCondition.PSN, oneLayer.side(P1).active().getStatus());

        Battle twoLayers = battleSwitchInto(Type.NORMAL);
        twoLayers.side(P1).addCondition(SideCondition.TOXIC_SPIKES);
        twoLayers.side(P1).addCondition(SideCondition.TOXIC_SPIKES);
        switchIn(twoLayers);
        assertEquals(StatusCondition.TOX, twoLayers.side(P1).active().getStatus());
    }

    @Test
    void poisonAbsorbsToxicSpikes() {
        Battle b = battleSwitchInto(Type.POISON);
        b.side(P1).addCondition(SideCondition.TOXIC_SPIKES);

        switchIn(b);

        assertEquals(StatusCondition.NONE, b.side(P1).active().getStatus());
        assertEquals(0, b.side(P1).getLayers(SideCondition.TOXIC_SPIKES));
    }

    @Test
    void stickyWebLowersSpeed() {
        Battle b = battleSwitchInto(Type.NORMAL);
        b.side(P1).addCondition(SideCondition.STICKY_WEB);

        switchIn(b);

        assertEquals(-1, b.side(P1).active().getStage(Stat.SPEED));
    }

    @Test
    void addConditionCapsAtMaxLayers() {
        Battle b = battleSwitchInto(Type.NORMAL);
        assertEquals(true, b.side(P1).addCondition(SideCondition.SPIKES));
        assertEquals(true, b.side(P1).addCondition(SideCondition.SPIKES));
        assertEquals(true, b.side(P1).addCondition(SideCondition.SPIKES));
        assertEquals(false, b.side(P1).addCondition(SideCondition.SPIKES)); // 4. warstwa odrzucona
        assertEquals(3, b.side(P1).getLayers(SideCondition.SPIKES));
    }
}
