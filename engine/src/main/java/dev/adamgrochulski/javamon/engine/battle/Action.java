package dev.adamgrochulski.javamon.engine.battle;

public sealed interface Action permits MoveAction, SwitchAction, ForfeitAction {

}
