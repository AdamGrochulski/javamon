package dev.adamgrochulski.javamon.engine.battle;

public enum Player {
    P1, P2;

    public Player opponent() {
        return this == P1 ? P2 : P1;
    }
}
