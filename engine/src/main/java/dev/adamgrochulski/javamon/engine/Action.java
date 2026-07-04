package dev.adamgrochulski.javamon.engine;

sealed interface Action permits MoveAction, SwitchAction, ForfeitAction {

}
