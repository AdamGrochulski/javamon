package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static dev.adamgrochulski.javamon.engine.battle.Player.P2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnResolverExecuteMoveTest {

    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 100, 100, 10, 0);
    private static final Move INACCURATE = new Move("Inaccurate", Type.NORMAL, MoveCategory.PHYSICAL, 100, 50, 10, 0);
    private static final Move GROWL = new Move("Growl", Type.NORMAL, MoveCategory.STATUS, 0, 100, 40, 0);

    // nextInt zawsze 100: accuracy(100) trafia, crit(6) nie, random(85..100) = 1.0.
    // Przy ruchu accuracy 50 -> 100 <= 50 false -> pudło.
    private static final Rng ROLL_100 = (min, max) -> 100;

    private static BattlePokemon poke(Type type, int level, Move move) {
        Stats base = new Stats(100, 100, 100, 100, 100, 100);
        return new BattlePokemon("P", base, type, null, level, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2, Rng rng) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), rng, new TypeChart());
    }

    @Test
    void hitDealsDamageAndEmitsMoveUsedThenDamage() {
        // atak WATER, ruch NORMAL (brak STAB), obrońca WATER (eff 1.0), level 50 -> maxHp 160
        // damage = 46 (jak w DamageCalculatorTest), remainingHp = 160 - 46 = 114
        Battle b = battle(poke(Type.WATER, 50, TACKLE), poke(Type.WATER, 50, TACKLE), ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));

        BattleEvent.Damage dmg = assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        assertEquals(P2, dmg.target().player());
        assertEquals(46, dmg.damage());
        assertEquals(114, dmg.remainingHp());
        assertEquals(114, b.side(P2).active().getCurrentHp()); // stan faktycznie zmieniony
    }

    @Test
    void missEmitsMoveMissedAndDealsNoDamage() {
        Battle b = battle(poke(Type.WATER, 50, INACCURATE), poke(Type.WATER, 50, TACKLE), ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        assertInstanceOf(BattleEvent.MoveMissed.class, events.get(1));
        assertEquals(b.side(P2).active().getMaxHp(), b.side(P2).active().getCurrentHp()); // HP nietknięte
    }

    @Test
    void immunityEmitsNoEffectAndDealsNoDamage() {
        // NORMAL vs GHOST = 0.0
        Battle b = battle(poke(Type.WATER, 50, TACKLE), poke(Type.GHOST, 50, TACKLE), ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        assertInstanceOf(BattleEvent.NoEffect.class, events.get(1));
        assertEquals(b.side(P2).active().getMaxHp(), b.side(P2).active().getCurrentHp());
    }

    @Test
    void lethalHitEmitsFaintAfterDamage() {
        // obrońca level 1 (maxHp 11, niska obrona) — ruch go zabija
        Battle b = battle(poke(Type.WATER, 50, TACKLE), poke(Type.NORMAL, 1, TACKLE), ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(3, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        assertInstanceOf(BattleEvent.Faint.class, events.get(2));
        assertTrue(b.side(P2).active().isFainted());
        assertEquals(0, b.side(P2).active().getCurrentHp());
    }

    @Test
    void paralyzedFullyImmobilizedEmitsOnlyImmobilized() {
        // rng=1 -> chance(25): 1<=25 true -> full para; return przed accuracy/damage
        Rng paraHits = (min, max) -> 1;
        BattlePokemon attacker = poke(Type.WATER, 50, TACKLE);
        attacker.applyStatus(StatusCondition.PAR);
        Battle b = battle(attacker, poke(Type.WATER, 50, TACKLE), paraHits);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(1, events.size());
        BattleEvent.Immobilized im = assertInstanceOf(BattleEvent.Immobilized.class, events.get(0));
        assertEquals(P1, im.who().player());
        assertEquals(StatusCondition.PAR, im.status());
        assertEquals(b.side(P2).active().getMaxHp(), b.side(P2).active().getCurrentHp()); // brak obrażeń
    }

    @Test
    void paralyzedButNotImmobilizedMovesNormally() {
        // ROLL_100 -> chance(25): 100<=25 false -> ruch idzie; damage jak zwykle (PAR nie tnie ataku)
        BattlePokemon attacker = poke(Type.WATER, 50, TACKLE);
        attacker.applyStatus(StatusCondition.PAR);
        Battle b = battle(attacker, poke(Type.WATER, 50, TACKLE), ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        BattleEvent.Damage dmg = assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        assertEquals(46, dmg.damage());
    }

    @Test
    void sleepBlocksForDurationThenWakesAndMoves() {
        BattlePokemon attacker = poke(Type.WATER, 50, TACKLE);
        attacker.applySleep(2); // dwie tury snu
        Battle b = battle(attacker, poke(Type.WATER, 50, TACKLE), ROLL_100);

        // tura 1: spał -> Immobilized SLP, nadal śpi
        List<BattleEvent> t1 = new ArrayList<>();
        TurnResolver.executeMove(b, P1, new MoveAction(0), t1);
        assertEquals(1, t1.size());
        BattleEvent.Immobilized im1 = assertInstanceOf(BattleEvent.Immobilized.class, t1.get(0));
        assertEquals(StatusCondition.SLP, im1.status());
        assertEquals(StatusCondition.SLP, attacker.getStatus());

        // tura 2: ostatnia tura snu -> Immobilized SLP, budzi się (NONE)
        List<BattleEvent> t2 = new ArrayList<>();
        TurnResolver.executeMove(b, P1, new MoveAction(0), t2);
        assertEquals(1, t2.size());
        assertInstanceOf(BattleEvent.Immobilized.class, t2.get(0));
        assertEquals(StatusCondition.NONE, attacker.getStatus());

        // tura 3: obudzony -> normalny ruch
        List<BattleEvent> t3 = new ArrayList<>();
        TurnResolver.executeMove(b, P1, new MoveAction(0), t3);
        assertEquals(2, t3.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, t3.get(0));
        BattleEvent.Damage dmg = assertInstanceOf(BattleEvent.Damage.class, t3.get(1));
        assertEquals(46, dmg.damage());
    }

    @Test
    void frozenBlockedWhenThawRollFails() {
        // ROLL_100 -> chance(20): 100<=20 false -> brak rozmrożenia -> blok
        BattlePokemon attacker = poke(Type.WATER, 50, TACKLE);
        attacker.applyStatus(StatusCondition.FRZ);
        Battle b = battle(attacker, poke(Type.WATER, 50, TACKLE), ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(1, events.size());
        BattleEvent.Immobilized im = assertInstanceOf(BattleEvent.Immobilized.class, events.get(0));
        assertEquals(StatusCondition.FRZ, im.status());
        assertEquals(StatusCondition.FRZ, attacker.getStatus()); // nadal zamrożony
        assertEquals(b.side(P2).active().getMaxHp(), b.side(P2).active().getCurrentHp());
    }

    @Test
    void frozenThawsAndMovesSameTurn() {
        // rng: chance-e (min=1) dostają 15 -> thaw(20) true, accuracy(100) true, crit(6) false;
        // random (min=85) dostaje 100 -> 1.0. Efekt: rozmraża i bije za 46.
        Rng thawAndHit = (min, max) -> min == 85 ? 100 : 15;
        BattlePokemon attacker = poke(Type.WATER, 50, TACKLE);
        attacker.applyStatus(StatusCondition.FRZ);
        Battle b = battle(attacker, poke(Type.WATER, 50, TACKLE), thawAndHit);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(StatusCondition.NONE, attacker.getStatus()); // rozmrożony
        assertEquals(2, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        BattleEvent.Damage dmg = assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        assertEquals(46, dmg.damage());
    }

    @Test
    void statusMoveInflictsStatusOnTarget() {
        // Will-O-Wisp: STATUS, nakłada BRN, accuracy 100
        Move willOWisp = new Move("Will-O-Wisp", Type.FIRE, MoveCategory.STATUS, 0, 100, 15, 0, StatusCondition.BRN);
        Battle b = battle(poke(Type.WATER, 50, willOWisp), poke(Type.WATER, 50, TACKLE), ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        BattleEvent.StatusInflicted si = assertInstanceOf(BattleEvent.StatusInflicted.class, events.get(1));
        assertEquals(P2, si.target().player());
        assertEquals(StatusCondition.BRN, si.status());
        assertEquals(StatusCondition.BRN, b.side(P2).active().getStatus());
    }

    @Test
    void statusMoveFailsWhenTargetAlreadyHasStatus() {
        // cel już zatruty -> Toxic nie nadpisze; tylko MoveUsed, brak StatusInflicted
        Move toxic = new Move("Toxic", Type.POISON, MoveCategory.STATUS, 0, 100, 10, 0, StatusCondition.TOX);
        BattlePokemon target = poke(Type.WATER, 50, TACKLE);
        target.applyStatus(StatusCondition.PSN);
        Battle b = battle(poke(Type.WATER, 50, toxic), target, ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(1, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        assertEquals(StatusCondition.PSN, target.getStatus()); // status nietknięty
    }

    @Test
    void sleepInflictingMoveRollsDurationAndAppliesSleep() {
        // Spore: STATUS, nakłada SLP. rng: chance-y (max=100) -> hit; duration nextInt(1,3) (max=3) -> 2
        Move spore = new Move("Spore", Type.GRASS, MoveCategory.STATUS, 0, 100, 15, 0, StatusCondition.SLP);
        Rng hitAndSleep2 = (min, max) -> max == 3 ? 2 : 100;
        Battle b = battle(poke(Type.WATER, 50, spore), poke(Type.WATER, 50, TACKLE), hitAndSleep2);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, events.size());
        BattleEvent.StatusInflicted si = assertInstanceOf(BattleEvent.StatusInflicted.class, events.get(1));
        assertEquals(StatusCondition.SLP, si.status());
        assertEquals(StatusCondition.SLP, b.side(P2).active().getStatus());
    }

    @Test
    void damagingMoveAppliesGuaranteedSecondaryStatus() {
        // ruch damage'owy z secondary 100% burn -> po obrażeniach cel dostaje BRN
        Move fireFang = new Move("Fire Fang", Type.NORMAL, MoveCategory.PHYSICAL, 100, 100, 15, 0,
                java.util.List.of(new MoveEffect.InflictStatus(StatusCondition.BRN, MoveEffect.Target.OPPONENT, 100)));
        Battle b = battle(poke(Type.WATER, 50, fireFang), poke(Type.WATER, 50, TACKLE), ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(3, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        BattleEvent.StatusInflicted si = assertInstanceOf(BattleEvent.StatusInflicted.class, events.get(2));
        assertEquals(StatusCondition.BRN, si.status());
        assertEquals(StatusCondition.BRN, b.side(P2).active().getStatus());
    }

    @Test
    void secondaryStatusSkippedWhenChanceRollFails() {
        // secondary 50%; ROLL_100 -> chance(50): 100<=50 false -> brak statusu, tylko damage
        Move fireFang = new Move("Fire Fang", Type.NORMAL, MoveCategory.PHYSICAL, 100, 100, 15, 0,
                java.util.List.of(new MoveEffect.InflictStatus(StatusCondition.BRN, MoveEffect.Target.OPPONENT, 50)));
        Battle b = battle(poke(Type.WATER, 50, fireFang), poke(Type.WATER, 50, TACKLE), ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(2, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        assertInstanceOf(BattleEvent.Damage.class, events.get(1));
        assertEquals(StatusCondition.NONE, b.side(P2).active().getStatus());
    }

    @Test
    void statusMoveEmitsOnlyMoveUsed() {
        Battle b = battle(poke(Type.WATER, 50, GROWL), poke(Type.WATER, 50, TACKLE), ROLL_100);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(1, events.size());
        assertInstanceOf(BattleEvent.MoveUsed.class, events.get(0));
        assertEquals(b.side(P2).active().getMaxHp(), b.side(P2).active().getCurrentHp());
    }
}
