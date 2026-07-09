package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.BattlePokemon;
import dev.adamgrochulski.javamon.engine.model.SideCondition;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class BattleSide {
    private final List<BattlePokemon> team;
    private int activeIndex;

    // Hazardy i inne efekty utrzymujące się po tej stronie (nie na konkretnym monie).
    private final Set<SideCondition> conditions = EnumSet.noneOf(SideCondition.class);

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

    /** Dodaje efekt strony; zwraca false, jeśli już był (bez podwajania). */
    public boolean addCondition(SideCondition condition) { return conditions.add(condition); }
    public boolean hasCondition(SideCondition condition) { return conditions.contains(condition); }

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
