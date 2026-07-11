package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.BattlePokemon;
import dev.adamgrochulski.javamon.engine.model.SideCondition;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BattleSide {
    private final List<BattlePokemon> team;
    private int activeIndex;

    // Hazardy i inne efekty utrzymujące się po tej stronie (nie na konkretnym monie).
    // Wartość = liczba warstw (Spikes 1-3, Toxic Spikes 1-2, reszta 1).
    private final Map<SideCondition, Integer> conditions = new EnumMap<>(SideCondition.class);

    public BattleSide(List<BattlePokemon> team) {
        if(team == null || team.isEmpty()) {
            throw new IllegalArgumentException("team jest wymagany");
        }

        if(team.size() > 6) {
            throw new IllegalArgumentException("team: max 6 pokemonow, było: " + team.size());
        }

        if(team.stream()
                .anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("team nie może zawierać null");
        }

        this.team = List.copyOf(team);

        activeIndex = 0;
    }

    public List<BattlePokemon> getTeam() { return team; }
    public int getActiveIndex() { return activeIndex; }

    /** Dodaje warstwę efektu (do maxLayers). Zwraca false, jeśli już przy limicie. */
    public boolean addCondition(SideCondition condition) {
        int current = conditions.getOrDefault(condition, 0);
        if (current >= condition.maxLayers()) {
            return false;
        }
        conditions.put(condition, current + 1);
        return true;
    }

    public boolean hasCondition(SideCondition condition) { return conditions.getOrDefault(condition, 0) > 0; }

    /** Liczba warstw efektu (0 = brak). */
    public int getLayers(SideCondition condition) { return conditions.getOrDefault(condition, 0); }

    /** Usuwa efekt całkowicie (np. Poison-type pochłania Toxic Spikes). */
    public void removeCondition(SideCondition condition) { conditions.remove(condition); }

    public BattlePokemon active() { return team.get(activeIndex); }
    public boolean isDefeated() {
        return team.stream()
                .allMatch(BattlePokemon::isFainted);
    }

    public void switchTo(int index) {
        if(index < 0 || index >= team.size()) {
            throw new IllegalArgumentException("index switcha poza zakresem: " + index);
        }
        if(index == activeIndex) {
            throw new IllegalArgumentException("Pokemon o indeksie " + index + " już jest aktywny");
        }

        if(team.get(index).isFainted()) {
            throw new IllegalArgumentException("Pokemon o indeksie " + index + " jest fainted");
        }
        activeIndex = index;
    }


}
