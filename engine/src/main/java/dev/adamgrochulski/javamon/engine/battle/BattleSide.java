package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.BattlePokemon;
import java.util.List;
import java.util.Objects;

public class BattleSide {
    private final List<BattlePokemon> team;
    private int activeIndex;

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
