package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.damage.DamageCalculator;
import dev.adamgrochulski.javamon.engine.damage.DamageResult;
import dev.adamgrochulski.javamon.engine.model.BattlePokemon;
import dev.adamgrochulski.javamon.engine.model.Move;
import dev.adamgrochulski.javamon.engine.model.MoveCategory;
import dev.adamgrochulski.javamon.engine.model.StatusCondition;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static dev.adamgrochulski.javamon.engine.battle.Player.P2;

public final class TurnResolver {
    private TurnResolver() {
    } // brak instancji

    // MVP — skróty do domknięcia:
    //  * Brak walidacji obu akcji PRZED wykonaniem. Resolver polega na guardach
    //    (moveAt/useMove/switchTo rzucają na nielegalne). Serwer (Faza 2) MUSI
    //    walidować intencję zanim zawoła resolve — to pierwsza linia anty-cheatu,
    //    guardy silnika to druga. Docelowo: walidacja obu akcji z góry (bez
    //    częściowej mutacji stanu gdy druga akcja okaże się nielegalna).
    //  * Zejście po faincie: resolve zostawia padły aktywny slot pusty i kończy
    //    turę. Serwer między turami woła awaitingReplacement() i dla każdego
    //    winnego gracza resolveReplacement(...). resolve broni się guardem —
    //    nie rozliczy nowej tury, dopóki wisi wybór zejścia.
    public static List<BattleEvent> resolve(Battle battle, Action p1Action, Action p2Action) {
        List<BattleEvent> events = new ArrayList<>();

        if (!battle.awaitingReplacement().isEmpty()) {
            throw new IllegalStateException("Nie można rozliczyć tury - wisi wybór zejścia: "
                    + battle.awaitingReplacement());
        }

        // 1. FORFEIT — short-circuit, kończy walkę natychmiast
        if (p1Action instanceof ForfeitAction) {
            events.add(new BattleEvent.Forfeit(P1));
            events.add(new BattleEvent.BattleEnd(P2));
            return events;
        }
        if (p2Action instanceof ForfeitAction) {
            events.add(new BattleEvent.Forfeit(P2));
            events.add(new BattleEvent.BattleEnd(P1));
            return events;
        }

        // 2. Kolejność
        for (Player p : order(battle, p1Action, p2Action)) {
            // 3. Cel padł od 1. akcji -> pomiń
            if (battle.side(p).active().isFainted()) continue;

            Action a = (p == P1) ? p1Action : p2Action;
            // 4. Dispatch po typie akcji
            switch (a) {
                case MoveAction m -> executeMove(battle, p, m, events);
                case SwitchAction s -> executeSwitch(battle, p, s, events);
                case ForfeitAction f -> {
                } // nie dojdzie (złapane wyżej)
            }

            // 5. Ktoś padł od ruchu -> koniec walki, pomiń ticki
            if (battle.isOver()) {
                events.add(new BattleEvent.BattleEnd(battle.winner()));
                return events;
            }
        }

        // 6. Ticki końca tury
        endOfTurnTicks(battle, events);

        // 7. Ticki mogłby dobić -> sprawdź ponownie
        if (battle.isOver()) {
            events.add(new BattleEvent.BattleEnd(battle.winner()));
            return events;
        }

        // 8. Kolejna tura
        battle.nextTurn();
        return events;
    }

    public static List<BattleEvent> resolveReplacement(Battle battle, Player player, SwitchAction action) {
        if (!battle.needsReplacement(player)) {
            throw new IllegalStateException("Gracz " + player + " nie musi wybierać zejścia");
        }
        List<BattleEvent> events = new ArrayList<>();

        BattleEvent.PokemonRef out = ref(battle, player);
        battle.side(player).switchTo(action.benchIndex());
        BattleEvent.PokemonRef in = ref(battle, player);
        events.add(new BattleEvent.Switch(out, in));

        return events;
    }

    private static List<Player> ordered(Player first) {
        return List.of(first, first.opponent());
    }

    private static int priorityOf(Battle battle, Player p, Action action) {
        if (action instanceof MoveAction(int moveIndex)) {
            return battle.side(p).active().moveAt(moveIndex).priority();
        }
        throw new IllegalStateException("priorityOf wołane nie dla MoveAction: " + action);
    }

    private static int effectiveSpeed(BattlePokemon mon) {
        int speed = mon.getSpeed();
        if (mon.getStatus() == StatusCondition.PAR) {
            speed = speed / 4;
        }

        return speed;
    }

    private static List<Player> firstBySpeed(Battle battle) {
        int s1 = effectiveSpeed(battle.side(P1).active());
        int s2 = effectiveSpeed(battle.side(P2).active());

        if (s1 != s2) {
            return s1 > s2 ? ordered(P1) : ordered(P2);
        }
        // remis speed -> RNG. Konwencja: chance(50)==true -> P1 pierwszy
        return battle.getRng().chance(50) ? ordered(P1) : ordered(P2);
    }

    static List<Player> order(Battle battle, Action p1Action, Action p2Action) {
        boolean p1Switch = p1Action instanceof SwitchAction;
        boolean p2Switch = p2Action instanceof SwitchAction;

        if (p1Switch && !p2Switch) {
            return ordered(P1);
        }

        if (!p1Switch && p2Switch) {
            return ordered(P2);
        }

        if (p1Switch) {
            return firstBySpeed(battle);
        }

        int pr1 = priorityOf(battle, P1, p1Action);
        int pr2 = priorityOf(battle, P2, p2Action);

        if (pr1 == pr2) return firstBySpeed(battle);

        return pr1 > pr2 ? ordered(P1) : ordered(P2);

    }

    private static BattleEvent.PokemonRef ref(Battle battle, Player p) {
        BattleSide side = battle.side(p);
        return new BattleEvent.PokemonRef(p, side.getActiveIndex(), side.active().getName());
    }

    static void executeMove(Battle battle, Player attacker, MoveAction action, List<BattleEvent> events) {
        Player defender = attacker.opponent();
        BattlePokemon atkMon = battle.side(attacker).active();
        BattlePokemon defMon = battle.side(defender).active();

        if (atkMon.getStatus() == StatusCondition.PAR && battle.getRng().chance(25)) {
            events.add(new BattleEvent.Immobilized(ref(battle, attacker),
                    StatusCondition.PAR));
            return;
        }

        Move move = atkMon.useMove(action.moveIndex());

        events.add(new BattleEvent.MoveUsed(ref(battle, attacker), move.name()));

        if (!battle.getRng().chance(move.accuracy())) {
            events.add(new BattleEvent.MoveMissed(ref(battle, attacker), move.name()));
            return;
        }

        // MVP: ruch STATUS trafia, ale efektów (nałożenie statusu, zmiana statów,
        // hazardy) jeszcze nie ma — sam MoveUsed poszedł, kończymy. Dojdzie później.
        if (move.category() == MoveCategory.STATUS) return;

        DamageResult result = DamageCalculator.calculate(atkMon, defMon, move, battle.getChart(), battle.getRng());

        if (result.noEffect()) {
            events.add(new BattleEvent.NoEffect(ref(battle, defender)));
            return;
        }

        defMon.takeDamage(result.damage());
        events.add(new BattleEvent.Damage(ref(battle, defender), result.damage(),
                defMon.getCurrentHp(), result.crit(), result.effectiveness()));

        if (defMon.isFainted()) events.add(new BattleEvent.Faint(ref(battle, defender)));
    }

    static void executeSwitch(Battle battle, Player player, SwitchAction action, List<BattleEvent> events) {
        BattleEvent.PokemonRef out = ref(battle, player);
        battle.side(player).switchTo(action.benchIndex());
        BattleEvent.PokemonRef in = ref(battle, player);
        events.add(new BattleEvent.Switch(out, in));
    }

    static void endOfTurnTicks(Battle battle, List<BattleEvent> events) {
        for (Player p : firstBySpeed(battle)) {
            BattlePokemon mon = battle.side(p).active();
            if (mon.isFainted()) continue;

            int dmg = mon.applyEndOfTurnDamage();
            if (dmg == 0) continue;
            events.add(new BattleEvent.StatusTick(ref(battle, p), mon.getStatus(), dmg,
                    mon.getCurrentHp()));
            if (mon.isFainted()) events.add(new BattleEvent.Faint(ref(battle, p)));
        }
    }
}
