package dev.adamgrochulski.javamon.engine.demo;

import dev.adamgrochulski.javamon.engine.battle.Battle;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import dev.adamgrochulski.javamon.engine.battle.BattleSide;
import dev.adamgrochulski.javamon.engine.battle.MoveAction;
import dev.adamgrochulski.javamon.engine.battle.TurnResolver;
import dev.adamgrochulski.javamon.engine.damage.TypeChart;
import dev.adamgrochulski.javamon.engine.model.BattlePokemon;
import dev.adamgrochulski.javamon.engine.model.Move;
import dev.adamgrochulski.javamon.engine.model.MoveCategory;
import dev.adamgrochulski.javamon.engine.model.Stats;
import dev.adamgrochulski.javamon.engine.model.StatusCondition;
import dev.adamgrochulski.javamon.engine.model.Type;
import dev.adamgrochulski.javamon.engine.rng.RandomRng;
import dev.adamgrochulski.javamon.engine.rng.Rng;

import java.util.List;

/**
 * Headless demo — silnik gra sam ze sobą i wypisuje przebieg na konsolę.
 * Obaj gracze zawsze używają ruchu 0; walka toczy się aż ktoś padnie.
 * Seed RNG stały, więc przebieg jest powtarzalny.
 */
public final class BattleDemo {

    public static void main(String[] args) {
        TypeChart chart = new TypeChart();
        Rng rng = new RandomRng(42);

        BattlePokemon charizard = new BattlePokemon(
                "Charizard", new Stats(78, 84, 78, 109, 85, 100),
                Type.FIRE, Type.FLYING, 50,
                List.of(new Move("Flamethrower", Type.FIRE, MoveCategory.SPECIAL, 90, 100, 15, 0)));

        BattlePokemon venusaur = new BattlePokemon(
                "Venusaur", new Stats(80, 82, 83, 100, 100, 80),
                Type.GRASS, Type.POISON, 50,
                List.of(new Move("Sludge Bomb", Type.POISON, MoveCategory.PHYSICAL, 90, 100, 10, 0)));

        // Dla pokazania eskalacji TOX — Venusaur wchodzi ciężko zatruty.
        venusaur.applyStatus(StatusCondition.TOX);

        Battle battle = new Battle(
                new BattleSide(List.of(charizard)),
                new BattleSide(List.of(venusaur)),
                rng, chart);

        System.out.println("=== Javamon — headless demo ===");
        System.out.println("Charizard (FIRE/FLYING) vs Venusaur (GRASS/POISON, zatruty TOX)\n");

        while (!battle.isOver()) {
            System.out.println("-- Tura " + battle.getTurn() + " --");
            List<BattleEvent> events = TurnResolver.resolve(battle, new MoveAction(0), new MoveAction(0));
            for (BattleEvent e : events) {
                System.out.println("  " + describe(e));
            }
            System.out.println();
        }
    }

    private static String describe(BattleEvent e) {
        return switch (e) {
            case BattleEvent.MoveUsed m   -> m.user().name() + " używa " + m.moveName();
            case BattleEvent.MoveMissed m -> m.user().name() + " chybia (" + m.moveName() + ")";
            case BattleEvent.Damage d     -> d.target().name() + " obrywa " + d.damage()
                    + " (zostaje " + d.remainingHp() + " HP)" + eff(d.effectiveness()) + (d.crit() ? " KRYTYK!" : "");
            case BattleEvent.NoEffect n   -> "Brak efektu na " + n.target().name();
            case BattleEvent.StatusTick t -> t.who().name() + " cierpi od " + t.status()
                    + " (-" + t.damage() + ", zostaje " + t.remainingHp() + " HP)";
            case BattleEvent.StatusInflicted si -> si.target().name() + " dostaje status " + si.status();
            case BattleEvent.StatStageChanged sc -> sc.who().name() + " " + sc.stat()
                    + (sc.delta() > 0 ? " +" : " ") + sc.delta() + " (stage " + sc.newStage() + ")";
            case BattleEvent.Healed h -> h.who().name() + " leczy się o " + h.amount()
                    + " (zostaje " + h.remainingHp() + " HP)";
            case BattleEvent.RecoilDamage r -> r.who().name() + " obrywa odrzutem " + r.damage()
                    + " (zostaje " + r.remainingHp() + " HP)";
            case BattleEvent.HazardSet hs -> "Hazard " + hs.condition() + " po stronie " + hs.side();
            case BattleEvent.HazardHurt hh -> hh.who().name() + " rani się o " + hh.condition()
                    + " (-" + hh.damage() + ", zostaje " + hh.remainingHp() + " HP)";
            case BattleEvent.Immobilized im -> im.who().name() + " nie może się ruszyć ("
                    + im.status() + ")";
            case BattleEvent.Flinched fl  -> fl.who().name() + " wzdryga się i traci turę!";
            case BattleEvent.ConfusionStarted cs -> cs.who().name() + " jest zmieszany!";
            case BattleEvent.ConfusionHit ch -> ch.who().name() + " w zmieszaniu rani siebie o " + ch.damage();
            case BattleEvent.ConfusionEnded ce -> ce.who().name() + " oprzytomniał";
            case BattleEvent.Charging cg  -> cg.who().name() + " ładuje " + cg.moveName() + "...";
            case BattleEvent.Recharging rc -> rc.who().name() + " musi odpocząć";
            case BattleEvent.Trapped tp   -> tp.who().name() + " zostaje uwięziony!";
            case BattleEvent.TrapHurt th  -> th.who().name() + " cierpi od uwięzienia " + th.damage();
            case BattleEvent.TrapEnded te -> te.who().name() + " uwalnia się";
            case BattleEvent.ProtectStarted ps -> ps.who().name() + " chroni się!";
            case BattleEvent.Protected pd -> "Atak zablokowany — " + pd.who().name() + " się obronił";
            case BattleEvent.MoveFailed mf -> mf.who().name() + " — " + mf.moveName() + " się nie udało";
            case BattleEvent.OneHitKO ko  -> ko.target().name() + " — natychmiastowy nokaut!";
            case BattleEvent.Seeded sd    -> sd.who().name() + " zostaje obsiany!";
            case BattleEvent.LeechSeedDrain ld -> ld.from().name() + " oddaje " + ld.amount() + " HP dla " + ld.to().name();
            case BattleEvent.WeatherStarted ws -> "Pogoda: " + ws.weather();
            case BattleEvent.WeatherHurt wh -> wh.who().name() + " obrywa od pogody " + wh.damage()
                    + " (zostaje " + wh.remainingHp() + " HP)";
            case BattleEvent.WeatherEnded we -> "Pogoda " + we.weather() + " mija";
            case BattleEvent.TerrainStarted ts -> "Teren: " + ts.terrain();
            case BattleEvent.TerrainEnded te2 -> "Teren " + te2.terrain() + " mija";
            case BattleEvent.ScreenSet sc -> "Ekran " + sc.condition() + " po stronie " + sc.side();
            case BattleEvent.ScreenFaded sf -> "Ekran " + sf.condition() + " znika (" + sf.side() + ")";
            case BattleEvent.Faint f      -> f.who().name() + " pada!";
            case BattleEvent.Switch s     -> s.out().name() + " schodzi, wchodzi " + s.in().name();
            case BattleEvent.Forfeit f    -> f.who() + " się poddaje";
            case BattleEvent.BattleEnd b  -> ">>> KONIEC — " + (b.winner() == null ? "remis" : "wygrywa " + b.winner());
        };
    }

    private static String eff(double effectiveness) {
        if (effectiveness == 0.0) return "";
        if (effectiveness > 1.0) return " (super skuteczne!)";
        if (effectiveness < 1.0) return " (mało skuteczne)";
        return "";
    }
}
