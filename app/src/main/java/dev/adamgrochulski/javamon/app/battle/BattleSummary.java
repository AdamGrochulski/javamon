package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.app.persistence.BattleResult;
import dev.adamgrochulski.javamon.engine.battle.Player;

import java.util.EnumMap;
import java.util.Map;

/** Rozliczenie zakończonej walki. Ratingi są w mapie, bo każdy gracz dostaje swój w BATTLE_END. */
public record BattleSummary(BattleResult result, Player winner,
                            Map<Player, Integer> ratingBefore, Map<Player, Integer> ratingAfter) {

    public BattleSummary {
        ratingBefore = new EnumMap<>(ratingBefore);
        ratingAfter = new EnumMap<>(ratingAfter);
    }
}
