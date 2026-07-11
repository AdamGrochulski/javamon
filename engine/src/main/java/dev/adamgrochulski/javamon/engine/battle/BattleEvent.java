package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.SideCondition;
import dev.adamgrochulski.javamon.engine.model.Stat;
import dev.adamgrochulski.javamon.engine.model.StatusCondition;
import dev.adamgrochulski.javamon.engine.model.Weather;

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

    record StatStageChanged(PokemonRef who, Stat stat, int delta, int newStage) implements BattleEvent {}

    record Healed(PokemonRef who, int amount, int remainingHp) implements BattleEvent {}

    record RecoilDamage(PokemonRef who, int damage, int remainingHp) implements BattleEvent {}

    record HazardSet(Player side, SideCondition condition) implements BattleEvent {}

    record HazardHurt(PokemonRef who, SideCondition condition, int damage, int remainingHp) implements BattleEvent {}

    record Immobilized(PokemonRef who, StatusCondition status) implements  BattleEvent {}

    record Flinched(PokemonRef who) implements BattleEvent {}

    record ConfusionStarted(PokemonRef who) implements BattleEvent {}

    record ConfusionHit(PokemonRef who, int damage, int remainingHp) implements BattleEvent {}

    record ConfusionEnded(PokemonRef who) implements BattleEvent {}

    record Charging(PokemonRef who, String moveName) implements BattleEvent {}

    record Recharging(PokemonRef who) implements BattleEvent {}

    record WeatherStarted(Weather weather) implements BattleEvent {}

    record WeatherHurt(PokemonRef who, Weather weather, int damage, int remainingHp) implements BattleEvent {}

    record WeatherEnded(Weather weather) implements BattleEvent {}

    record ScreenSet(Player side, SideCondition condition) implements BattleEvent {}

    record ScreenFaded(Player side, SideCondition condition) implements BattleEvent {}

    record Forfeit(Player who) implements BattleEvent {}

    record BattleEnd(Player winner) implements BattleEvent {}



}
