package dev.adamgrochulski.javamon.engine.battle;

public record MoveAction(int moveIndex) implements Action {
    public MoveAction {
        if (moveIndex < 0) {
            throw new IllegalArgumentException("moveIndex nie moze byc ujemny, bylo: " + moveIndex);
        }
    }
}
