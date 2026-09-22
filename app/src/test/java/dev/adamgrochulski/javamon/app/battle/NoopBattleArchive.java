package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.app.persistence.BattleResult;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import dev.adamgrochulski.javamon.engine.battle.Player;

import java.util.List;
import java.util.Map;

/** Atrapa archiwum: testy jednostkowe serwisu nie dotykają bazy. */
class NoopBattleArchive implements BattleArchive {

    @Override
    public BattleSummary archive(BattleSession session, BattleResult result,
                                 Player winner, List<BattleEvent> events) {
        Map<Player, Integer> unchanged = Map.of(
                Player.P1, session.participant(Player.P1).rating(),
                Player.P2, session.participant(Player.P2).rating());
        return new BattleSummary(result, winner, unchanged, unchanged);
    }
}
