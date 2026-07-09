package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.damage.DamageCalculator;
import dev.adamgrochulski.javamon.engine.damage.DamageResult;
import dev.adamgrochulski.javamon.engine.model.BattlePokemon;
import dev.adamgrochulski.javamon.engine.damage.TypeChart;
import dev.adamgrochulski.javamon.engine.model.Move;
import dev.adamgrochulski.javamon.engine.model.MoveCategory;
import dev.adamgrochulski.javamon.engine.model.MoveEffect;
import dev.adamgrochulski.javamon.engine.model.SideCondition;
import dev.adamgrochulski.javamon.engine.model.Stat;
import dev.adamgrochulski.javamon.engine.model.StatusCondition;
import dev.adamgrochulski.javamon.engine.model.Type;
import dev.adamgrochulski.javamon.engine.model.Weather;

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

        // 6. Ticki końca tury (status + pogoda + ekrany)
        endOfTurnTicks(battle, events);
        weatherEndOfTurn(battle, events);
        tickScreens(battle, events);

        // 7. Ticki mogłby dobić -> sprawdź ponownie
        if (battle.isOver()) {
            events.add(new BattleEvent.BattleEnd(battle.winner()));
            return events;
        }

        // 8. Kasowanie volatile'ów jednorurowych (flinch) i kolejna tura
        battle.side(P1).active().clearTurnVolatiles();
        battle.side(P2).active().clearTurnVolatiles();
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

        applyEntryHazards(battle, player, events);

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
        // Warstwy: stopień statu (getEffectiveSpeed) -> potem status (PAR ćwiartuje).
        int speed = mon.getEffectiveSpeed();
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

        // Blokady ruchu od statusu — sprawdzane PRZED useMove, bo nie zużywają PP.

        // Sen: twardy blok bez rzutu, konsumuje turę i budzi po odliczeniu.
        if (atkMon.sleepTurn()) {
            events.add(new BattleEvent.Immobilized(ref(battle, attacker),
                    StatusCondition.SLP));
            return;
        }

        // Paraliż: 25% szans na full-para (rzut RNG tylko gdy status PAR).
        if (atkMon.getStatus() == StatusCondition.PAR && battle.getRng().chance(25)) {
            events.add(new BattleEvent.Immobilized(ref(battle, attacker),
                    StatusCondition.PAR));
            return;
        }

        // Zamrożenie: 20% szans na rozmrożenie/turę; rozmrożony rusza się w tej turze,
        // inaczej blok (rzut RNG tylko gdy FRZ).
        if (atkMon.getStatus() == StatusCondition.FRZ) {
            if (battle.getRng().chance(20)) {
                atkMon.thaw();
            } else {
                events.add(new BattleEvent.Immobilized(ref(battle, attacker),
                        StatusCondition.FRZ));
                return;
            }
        }

        // Flinch (wzdrygnięcie): volatile ustawiony przez szybszego atakującego w tej
        // turze. Blokuje ruch, nie zużywa PP. Kasowany na końcu tury.
        if (atkMon.isFlinched()) {
            events.add(new BattleEvent.Flinched(ref(battle, attacker)));
            return;
        }

        Move move = atkMon.useMove(action.moveIndex());

        events.add(new BattleEvent.MoveUsed(ref(battle, attacker), move.name()));

        if (!battle.getRng().chance(move.accuracy())) {
            events.add(new BattleEvent.MoveMissed(ref(battle, attacker), move.name()));
            return;
        }

        // Ruch STATUS: trafił (accuracy wyżej), więc odpala swoje efekty i kończy.
        // Nie zadaje obrażeń -> damageDealt = 0 (Recoil/Drain nie mają z czego liczyć).
        if (move.category() == MoveCategory.STATUS) {
            applyEffects(battle, attacker, defender, move, 0, events);
            return;
        }

        // Multi-hit: liczba uderzeń rollowana raz, każde uderzenie liczone osobno
        // (własny krytyk/roll). Pojedynczy ruch = dokładnie 1 uderzenie.
        Move.MultiHit mh = move.multiHit();
        int hits = (mh == null) ? 1 : rollHits(mh, battle.getRng());
        int total = 0;
        boolean immune = false;
        boolean screened = isScreened(battle.side(defender), move.category());

        for (int i = 0; i < hits; i++) {
            if (defMon.isFainted()) break;
            DamageResult result = DamageCalculator.calculate(atkMon, defMon, move,
                    battle.getChart(), battle.getRng(), battle.getWeather(), screened);

            // Immunność typu jest stała między uderzeniami — łapiemy na 1. i kończymy.
            if (result.noEffect()) {
                immune = true;
                break;
            }

            defMon.takeDamage(result.damage());
            total += result.damage();
            events.add(new BattleEvent.Damage(ref(battle, defender), result.damage(),
                    defMon.getCurrentHp(), result.crit(), result.effectiveness()));

            if (defMon.isFainted()) {
                events.add(new BattleEvent.Faint(ref(battle, defender)));
                break;
            }
        }

        if (immune && total == 0) {
            events.add(new BattleEvent.NoEffect(ref(battle, defender)));
            return;
        }

        // Secondary effects — PO obrażeniach, raz, na sumie zadanych obrażeń (recoil/drain).
        applyEffects(battle, attacker, defender, move, total, events);
    }

    // Liczba uderzeń ruchu wielokrotnego. Stała gdy min==max; dla [2,5] rozkład
    // jak w grach (gen5+): 2 i 3 po 3/8, 4 i 5 po 1/8; inne przedziały jednostajnie.
    private static int rollHits(Move.MultiHit mh, dev.adamgrochulski.javamon.engine.rng.Rng rng) {
        if (mh.min() == mh.max()) {
            return mh.min();
        }
        if (mh.min() == 2 && mh.max() == 5) {
            int r = rng.nextInt(1, 8);
            if (r <= 3) return 2;
            if (r <= 6) return 3;
            if (r == 7) return 4;
            return 5;
        }
        return rng.nextInt(mh.min(), mh.max());
    }

    /**
     * Odpala efekty uboczne ruchu. Dla każdego: rzut szansy (100% bez rzutu RNG,
     * żeby nie ruszać sekwencji ruchów w pełni deterministycznych), potem rozwiązanie
     * celu (SELF/OPPONENT) i wykonanie wariantu efektu. {@code damageDealt} = obrażenia
     * zadane przez ruch (0 dla STATUS) — potrzebne przez Recoil/Drain.
     */
    static void applyEffects(Battle battle, Player attacker, Player defender, Move move, int damageDealt, List<BattleEvent> events) {
        for (MoveEffect effect : move.effects()) {
            boolean triggers = effect.chance() >= 100 || battle.getRng().chance(effect.chance());
            if (!triggers) continue;

            Player who = (effect.target() == MoveEffect.Target.SELF) ? attacker : defender;
            BattlePokemon target = battle.side(who).active();

            switch (effect) {
                case MoveEffect.InflictStatus is -> {
                    if (target.isFainted()) break;   // nie nakładamy na trupa
                    boolean applied = (is.status() == StatusCondition.SLP)
                            ? target.applySleep(battle.getRng().nextInt(1, 3))
                            : target.applyStatus(is.status());
                    if (applied) {
                        events.add(new BattleEvent.StatusInflicted(ref(battle, who), is.status()));
                    }
                }
                case MoveEffect.StatChange sc -> {
                    if (target.isFainted()) break;   // trup nie boostuje statów
                    int applied = target.changeStage(sc.stat(), sc.stages());
                    if (applied != 0) {   // 0 = stat już przy limicie -> bez eventu
                        events.add(new BattleEvent.StatStageChanged(ref(battle, who),
                                sc.stat(), applied, target.getStage(sc.stat())));
                    }
                }
                case MoveEffect.Heal h -> {
                    if (target.isFainted()) break;
                    int healed = target.heal(Math.max(1, target.getMaxHp() * h.percent() / 100));
                    if (healed > 0) {
                        events.add(new BattleEvent.Healed(ref(battle, who), healed, target.getCurrentHp()));
                    }
                }
                case MoveEffect.Recoil r -> {
                    if (damageDealt <= 0) break;   // brak obrażeń -> brak odrzutu
                    int amount = Math.max(1, damageDealt * r.percent() / 100);
                    target.takeDamage(amount);
                    events.add(new BattleEvent.RecoilDamage(ref(battle, who), amount, target.getCurrentHp()));
                    if (target.isFainted()) {
                        events.add(new BattleEvent.Faint(ref(battle, who)));
                    }
                }
                case MoveEffect.Drain d -> {
                    if (damageDealt <= 0) break;
                    int healed = target.heal(Math.max(1, damageDealt * d.percent() / 100));
                    if (healed > 0) {
                        events.add(new BattleEvent.Healed(ref(battle, who), healed, target.getCurrentHp()));
                    }
                }
                case MoveEffect.Hazard hz -> {
                    // Hazard idzie na STRONĘ (who), nie na konkretnego mona.
                    if (battle.side(who).addCondition(hz.condition())) {
                        events.add(new BattleEvent.HazardSet(who, hz.condition()));
                    }
                }
                case MoveEffect.SetWeather sw -> {
                    // Pogoda globalna — nie dotyczy konkretnego mona. Standard: 5 tur.
                    battle.setWeather(sw.weather(), 5);
                    events.add(new BattleEvent.WeatherStarted(sw.weather()));
                }
                case MoveEffect.SetScreen ss -> {
                    // Ekran na stronie używającego (who = SELF). Aurora Veil tylko w śniegu.
                    boolean auroraOk = ss.condition() != SideCondition.AURORA_VEIL
                            || battle.getWeather() == Weather.SNOW;
                    if (auroraOk && battle.side(who).addTimedCondition(ss.condition(), 5)) {
                        events.add(new BattleEvent.ScreenSet(who, ss.condition()));
                    }
                }
                case MoveEffect.Flinch f -> {
                    // Ustawia volatile; realny skutek (utrata tury) zależy od kolejności —
                    // sprawdzany w executeMove, gdy flincher próbuje się ruszyć.
                    if (!target.isFainted()) {
                        target.flinch();
                    }
                }
                case MoveEffect.ForceSelfSwitch fs -> {
                    // Pivot: używający (who = SELF) wycofuje się na następnego żywego z ławki.
                    BattleSide side = battle.side(who);
                    int next = nextLivingBench(side);
                    if (next >= 0) {
                        BattleEvent.PokemonRef out = ref(battle, who);
                        side.switchTo(next);
                        events.add(new BattleEvent.Switch(out, ref(battle, who)));
                        applyEntryHazards(battle, who, events);
                    }
                }
            }
        }
    }

    static void executeSwitch(Battle battle, Player player, SwitchAction action, List<BattleEvent> events) {
        BattleEvent.PokemonRef out = ref(battle, player);
        battle.side(player).switchTo(action.benchIndex());
        BattleEvent.PokemonRef in = ref(battle, player);
        events.add(new BattleEvent.Switch(out, in));

        applyEntryHazards(battle, player, events);
    }

    /**
     * Efekty wejściowe od hazardów po stronie gracza. Wołane po każdej zmianie
     * aktywnego (zwykły switch i replacement po faincie). Kolejność: Stealth Rock,
     * Spikes, Toxic Spikes, Sticky Web. Naziemność przybliżamy typem (Flying = lata,
     * niekryty przez hazardy kontaktowe) — brak jeszcze itemów/abilities.
     */
    static void applyEntryHazards(Battle battle, Player player, List<BattleEvent> events) {
        BattleSide side = battle.side(player);
        BattlePokemon mon = side.active();
        if (mon.isFainted()) {
            return;
        }

        // Stealth Rock — trafia każdego (typowe, nie kontaktowe).
        if (side.hasCondition(SideCondition.STEALTH_ROCK)) {
            double eff = rockEffectiveness(battle.getChart(), mon);
            int dmg = Math.max(1, (int) (mon.getMaxHp() * eff / 8.0));
            if (hazardHurt(battle, player, mon, SideCondition.STEALTH_ROCK, dmg, events)) {
                return;   // padł -> stop
            }
        }

        boolean grounded = !isType(mon, Type.FLYING);
        if (!grounded) {
            return;   // reszta hazardów działa tylko na naziemnych
        }

        // Spikes — obrażenia rosną z warstwami: 1 -> 1/8, 2 -> 1/6, 3 -> 1/4 maxHp.
        int spikes = side.getLayers(SideCondition.SPIKES);
        if (spikes > 0) {
            int denom = switch (spikes) {
                case 1 -> 8;
                case 2 -> 6;
                default -> 4;
            };
            int dmg = Math.max(1, mon.getMaxHp() / denom);
            if (hazardHurt(battle, player, mon, SideCondition.SPIKES, dmg, events)) {
                return;
            }
        }

        // Toxic Spikes — Poison naziemny pochłania; Steel odporny; reszta dostaje
        // PSN (1 warstwa) lub TOX (2 warstwy).
        int toxicSpikes = side.getLayers(SideCondition.TOXIC_SPIKES);
        if (toxicSpikes > 0) {
            if (isType(mon, Type.POISON)) {
                side.removeCondition(SideCondition.TOXIC_SPIKES);
            } else if (!isType(mon, Type.STEEL)) {
                StatusCondition status = toxicSpikes >= 2 ? StatusCondition.TOX : StatusCondition.PSN;
                if (mon.applyStatus(status)) {
                    events.add(new BattleEvent.StatusInflicted(ref(battle, player), status));
                }
            }
        }

        // Sticky Web — obniża Speed o 1 stopień.
        if (side.hasCondition(SideCondition.STICKY_WEB)) {
            int applied = mon.changeStage(Stat.SPEED, -1);
            if (applied != 0) {
                events.add(new BattleEvent.StatStageChanged(ref(battle, player), Stat.SPEED,
                        applied, mon.getStage(Stat.SPEED)));
            }
        }
    }

    // Zadaje obrażenia od hazardu, emituje HazardHurt (+ Faint). Zwraca true, gdy mon padł.
    private static boolean hazardHurt(Battle battle, Player player, BattlePokemon mon,
                                      SideCondition condition, int dmg, List<BattleEvent> events) {
        mon.takeDamage(dmg);
        events.add(new BattleEvent.HazardHurt(ref(battle, player), condition, dmg, mon.getCurrentHp()));
        if (mon.isFainted()) {
            events.add(new BattleEvent.Faint(ref(battle, player)));
            return true;
        }
        return false;
    }

    // Pierwszy żywy Pokémon na ławce (poza aktywnym); -1 gdy brak zmiennika.
    private static int nextLivingBench(BattleSide side) {
        for (int i = 0; i < side.getTeam().size(); i++) {
            if (i != side.getActiveIndex() && !side.getTeam().get(i).isFainted()) {
                return i;
            }
        }
        return -1;
    }

    // Efektywność typu ROCK względem typów wchodzącego (iloczyn po obu typach).
    private static double rockEffectiveness(TypeChart chart, BattlePokemon mon) {
        double eff = chart.multiplier(Type.ROCK, mon.getPrimary());
        if (mon.getSecondary() != null) {
            eff *= chart.multiplier(Type.ROCK, mon.getSecondary());
        }
        return eff;
    }

    /**
     * Koniec tury: chip pogodowy (na razie tylko SANDSTORM rani niekryte typy),
     * potem odliczenie trwania. SANDSTORM nie rani Rock/Ground/Steel.
     */
    static void weatherEndOfTurn(Battle battle, List<BattleEvent> events) {
        Weather weather = battle.getWeather();
        if (weather == Weather.NONE) {
            return;
        }

        if (weather == Weather.SANDSTORM) {
            for (Player p : firstBySpeed(battle)) {
                BattlePokemon mon = battle.side(p).active();
                if (mon.isFainted() || immuneToSandstorm(mon)) {
                    continue;
                }
                int dmg = Math.max(1, mon.getMaxHp() / 16);
                mon.takeDamage(dmg);
                events.add(new BattleEvent.WeatherHurt(ref(battle, p), weather, dmg, mon.getCurrentHp()));
                if (mon.isFainted()) {
                    events.add(new BattleEvent.Faint(ref(battle, p)));
                }
            }
        }

        if (battle.tickWeather()) {
            events.add(new BattleEvent.WeatherEnded(weather));
        }
    }

    private static boolean immuneToSandstorm(BattlePokemon mon) {
        return isType(mon, Type.ROCK) || isType(mon, Type.GROUND) || isType(mon, Type.STEEL);
    }

    private static boolean isType(BattlePokemon mon, Type type) {
        return mon.getPrimary() == type || mon.getSecondary() == type;
    }

    // Czy strona obrońcy ma ekran pasujący do kategorii ruchu (Aurora Veil kryje obie).
    private static boolean isScreened(BattleSide defenderSide, MoveCategory category) {
        if (category == MoveCategory.PHYSICAL) {
            return defenderSide.hasCondition(SideCondition.REFLECT)
                    || defenderSide.hasCondition(SideCondition.AURORA_VEIL);
        }
        if (category == MoveCategory.SPECIAL) {
            return defenderSide.hasCondition(SideCondition.LIGHT_SCREEN)
                    || defenderSide.hasCondition(SideCondition.AURORA_VEIL);
        }
        return false;
    }

    // Odlicza ekrany po obu stronach na końcu tury; emituje ScreenFaded dla wygasłych.
    static void tickScreens(Battle battle, List<BattleEvent> events) {
        for (Player p : List.of(P1, P2)) {
            for (SideCondition expired : battle.side(p).tickTimedConditions()) {
                events.add(new BattleEvent.ScreenFaded(p, expired));
            }
        }
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
