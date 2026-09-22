package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.app.persistence.BattleResult;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import dev.adamgrochulski.javamon.engine.battle.Player;

import java.util.List;

/** Zapis zakończonej walki: wynik, ratingi, replay. Testy jednostkowe podstawiają atrapę. */
public interface BattleArchive {

    BattleSummary archive(BattleSession session, BattleResult result, Player winner, List<BattleEvent> events);
}
