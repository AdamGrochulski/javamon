package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.StatusCondition;

public sealed interface BattleEvent {

    record PokemonRef(Player player, int teamIndex, String name) {}

    record Switch(PokemonRef out, PokemonRef in) implements BattleEvent {}

    record MoveUsed(PokemonRef user, String moveName) implements BattleEvent {}

    record MoveMissed(PokemonRef user, String moveName) implements BattleEvent {}

    record Damage(PokemonRef target, int damage, int remainingHp, boolean crit, double effectiveness) implements BattleEvent {}

    record NoEffect(PokemonRef target) implements BattleEvent {}

    record Faint(PokemonRef who) implements BattleEvent {}

    record StatusTick(PokemonRef who, StatusCondition status, int damage, int remainingHp) implements BattleEvent {}

    record StatusInflicted(PokemonRef target, StatusCondition status) implements BattleEvent {}

    record Immobilized(PokemonRef who, StatusCondition status) implements  BattleEvent {}

    record Forfeit(Player who) implements BattleEvent {}

    record BattleEnd(Player winner) implements BattleEvent {}



}
