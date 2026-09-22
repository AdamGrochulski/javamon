package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.engine.battle.Battle;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import dev.adamgrochulski.javamon.engine.battle.Player;

import java.util.List;

/**
 * Przepisuje eventy pod konkretnego odbiorcę: HP przeciwnika w procentach,
 * własne w punktach. Filtrowanie musi być tutaj, nie w UI - wystarczy
 * zakładka Network, żeby zobaczyć surową ramkę.
 */
public final class BattleEventFilter {

    private BattleEventFilter() {
    }

    public static List<BattleEvent> forPlayer(Battle battle, Player recipient, List<BattleEvent> events) {
        return events.stream().map(e -> filter(battle, recipient, e)).toList();
    }

    // Switch bez default: nowy wariant eventu ma nie przejść kompilacji, dopóki
    // ktoś nie zdecyduje, czy niesie HP. Domyślna gałąź przepuściłaby go po cichu.
    private static BattleEvent filter(Battle battle, Player to, BattleEvent event) {
        return switch (event) {

            case BattleEvent.Damage(var target, int dmg, int hp, boolean crit, double eff) ->
                    own(to, target) ? event
                            : new BattleEvent.Damage(target, pct(battle, target, dmg), pct(battle, target, hp), crit, eff);

            case BattleEvent.StatusTick(var who, var status, int dmg, int hp) ->
                    own(to, who) ? event
                            : new BattleEvent.StatusTick(who, status, pct(battle, who, dmg), pct(battle, who, hp));

            case BattleEvent.Healed(var who, int amount, int hp) ->
                    own(to, who) ? event
                            : new BattleEvent.Healed(who, pct(battle, who, amount), pct(battle, who, hp));

            case BattleEvent.RecoilDamage(var who, int dmg, int hp) ->
                    own(to, who) ? event
                            : new BattleEvent.RecoilDamage(who, pct(battle, who, dmg), pct(battle, who, hp));

            case BattleEvent.HazardHurt(var who, var cond, int dmg, int hp) ->
                    own(to, who) ? event
                            : new BattleEvent.HazardHurt(who, cond, pct(battle, who, dmg), pct(battle, who, hp));

            case BattleEvent.ConfusionHit(var who, int dmg, int hp) ->
                    own(to, who) ? event
                            : new BattleEvent.ConfusionHit(who, pct(battle, who, dmg), pct(battle, who, hp));

            case BattleEvent.TrapHurt(var who, int dmg, int hp) ->
                    own(to, who) ? event
                            : new BattleEvent.TrapHurt(who, pct(battle, who, dmg), pct(battle, who, hp));

            case BattleEvent.WeatherHurt(var who, var weather, int dmg, int hp) ->
                    own(to, who) ? event
                            : new BattleEvent.WeatherHurt(who, weather, pct(battle, who, dmg), pct(battle, who, hp));

            // amount liczone jest z maxHp obsianego (from), więc przelicza się
            // tylko wtedy, gdy to przeciwnik traci HP.
            case BattleEvent.LeechSeedDrain(var from, var target, int amount) ->
                    own(to, from) ? event
                            : new BattleEvent.LeechSeedDrain(from, target, pct(battle, from, amount));

            case BattleEvent.Switch ignored -> event;
            case BattleEvent.MoveUsed ignored -> event;
            case BattleEvent.MoveMissed ignored -> event;
            case BattleEvent.NoEffect ignored -> event;
            case BattleEvent.Faint ignored -> event;
            case BattleEvent.StatusInflicted ignored -> event;
            case BattleEvent.StatStageChanged ignored -> event;
            case BattleEvent.HazardSet ignored -> event;
            case BattleEvent.Immobilized ignored -> event;
            case BattleEvent.Flinched ignored -> event;
            case BattleEvent.ConfusionStarted ignored -> event;
            case BattleEvent.ConfusionEnded ignored -> event;
            case BattleEvent.Charging ignored -> event;
            case BattleEvent.Recharging ignored -> event;
            case BattleEvent.Trapped ignored -> event;
            case BattleEvent.TrapEnded ignored -> event;
            case BattleEvent.ProtectStarted ignored -> event;
            case BattleEvent.Protected ignored -> event;
            case BattleEvent.MoveFailed ignored -> event;
            case BattleEvent.OneHitKO ignored -> event;
            case BattleEvent.Seeded ignored -> event;
            case BattleEvent.WeatherStarted ignored -> event;
            case BattleEvent.WeatherEnded ignored -> event;
            case BattleEvent.ScreenSet ignored -> event;
            case BattleEvent.ScreenFaded ignored -> event;
            case BattleEvent.TerrainStarted ignored -> event;
            case BattleEvent.TerrainEnded ignored -> event;
            case BattleEvent.Forfeit ignored -> event;
            case BattleEvent.BattleEnd ignored -> event;
        };
    }

    private static boolean own(Player recipient, BattleEvent.PokemonRef ref) {
        return ref.player() == recipient;
    }

    /** Procent maksymalnego HP, w górę. Zero tylko przy zerze punktów - pasek nie może kłamać o faincie. */
    public static int percentOf(int points, int maxHp) {
        if (points <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(points * 100.0 / maxHp));
    }

    private static int pct(Battle battle, BattleEvent.PokemonRef ref, int points) {
        return percentOf(points, battle.side(ref.player()).getTeam().get(ref.teamIndex()).getMaxHp());
    }
}
