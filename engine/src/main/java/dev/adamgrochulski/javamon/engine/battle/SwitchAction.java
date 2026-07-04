package dev.adamgrochulski.javamon.engine.battle;

public record SwitchAction(int benchIndex) implements Action {
    public SwitchAction {
        if(benchIndex < 0) {
            throw new IllegalArgumentException("benchIndex nie może być ujemny, było: " + benchIndex);
        }
    }
}
