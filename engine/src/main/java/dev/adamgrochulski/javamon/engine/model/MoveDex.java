package dev.adamgrochulski.javamon.engine.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Katalog ruchów wczytany z /moves.json. Dane oddzielone od kodu (jak TypeChart);
 * dodanie ruchu = wiersz w JSON, zero zmian w silniku.
 * <p>
 * Efekty w JSON są opisane dyskryminatorem "kind" i mapowane ręcznie na
 * {@link MoveEffect} — prościej niż konfigurować polimorficzną deserializację.
 */
public class MoveDex {

    // Pośredni DTO ruchu z JSON-a. simplified = ma efekt, którego silnik jeszcze
    // nie modeluje (ruch ładuje się z podstawą, ale nie odtwarza pełnego działania).
    private record MoveEntry(String name, Type type, MoveCategory category,
                             int power, int accuracy, int pp, int priority,
                             List<EffectEntry> effects, int[] multihit, Move.TwoTurn twoTurn,
                             boolean simplified) {}

    // Pośredni DTO efektu — pola opcjonalne (null gdy nieużywane przy danym kind).
    private record EffectEntry(String kind, StatusCondition status, Stat stat, Integer stages,
                               Integer percent, SideCondition condition, Weather weather,
                               MoveEffect.Target target, Integer chance) {}

    private final Map<String, Move> byName;
    private final Set<String> simplified;

    public MoveDex() {
        Map<String, Move> map = new LinkedHashMap<>();
        Set<String> simp = new HashSet<>();
        for (MoveEntry entry : load()) {
            Move move = toMove(entry);
            if (map.putIfAbsent(move.name(), move) != null) {
                throw new IllegalStateException("Zduplikowany ruch w moves.json: " + move.name());
            }
            if (entry.simplified()) {
                simp.add(move.name());
            }
        }
        this.byName = Map.copyOf(map);
        this.simplified = Set.copyOf(simp);
    }

    /** Ruch po nazwie. Rzuca, gdy nieznany — serwer i tak waliduje moveset gracza wcześniej. */
    public Move get(String name) {
        Move move = byName.get(name);
        if (move == null) {
            throw new IllegalArgumentException("Nieznany ruch: " + name);
        }
        return move;
    }

    public boolean has(String name) { return byName.containsKey(name); }

    public Set<String> names() { return byName.keySet(); }

    public int size() { return byName.size(); }

    /** true = ruch ma efekt jeszcze nieodtwarzany przez silnik (załadowany z podstawą). */
    public boolean isSimplified(String name) { return simplified.contains(name); }

    public int simplifiedCount() { return simplified.size(); }

    private static Move toMove(MoveEntry e) {
        List<MoveEffect> effects = e.effects() == null
                ? List.of()
                : e.effects().stream().map(MoveDex::toEffect).toList();
        Move.MultiHit multiHit = (e.multihit() != null && e.multihit().length == 2)
                ? new Move.MultiHit(e.multihit()[0], e.multihit()[1])
                : null;
        Move.TwoTurn twoTurn = e.twoTurn() == null ? Move.TwoTurn.NONE : e.twoTurn();
        return new Move(e.name(), e.type(), e.category(), e.power(), e.accuracy(), e.pp(), e.priority(),
                effects, multiHit, twoTurn);
    }

    private static MoveEffect toEffect(EffectEntry e) {
        return switch (e.kind()) {
            case "inflictStatus" -> new MoveEffect.InflictStatus(e.status(), e.target(), e.chance());
            case "statChange" -> new MoveEffect.StatChange(e.stat(), e.stages(), e.target(), e.chance());
            case "heal" -> new MoveEffect.Heal(e.percent(), e.target(), e.chance());
            case "recoil" -> new MoveEffect.Recoil(e.percent());
            case "drain" -> new MoveEffect.Drain(e.percent());
            case "hazard" -> new MoveEffect.Hazard(e.condition());
            case "forceSelfSwitch" -> new MoveEffect.ForceSelfSwitch();
            case "flinch" -> new MoveEffect.Flinch(e.chance());
            case "confuse" -> new MoveEffect.Confuse(e.target(), e.chance());
            case "trap" -> new MoveEffect.Trap(e.target(), e.chance());
            case "setWeather" -> new MoveEffect.SetWeather(e.weather());
            case "setScreen" -> new MoveEffect.SetScreen(e.condition());
            default -> throw new IllegalStateException("Nieznany kind efektu w moves.json: " + e.kind());
        };
    }

    private static MoveEntry[] load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = MoveDex.class.getResourceAsStream("/moves.json")) {
            if (in == null) {
                throw new IllegalStateException("Brak moves.json na classpath");
            }
            return mapper.readValue(in, MoveEntry[].class);
        } catch (IOException ex) {
            throw new UncheckedIOException("Błąd czytania moves.json", ex);
        }
    }
}
