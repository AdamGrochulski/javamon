package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.engine.battle.BattleEvent;

import java.util.List;

/**
 * Wynik akcji razem ze stanem walki po niej. Sesja musi wracać z serwisu, bo
 * warstwa WS filtruje eventy względem stanu i kopia sprzed akcji dałaby złe HP.
 */
public record TurnOutcome(BattleSession session, int turn, List<BattleEvent> events, BattleSummary summary) {

    /** Wypełnione dopiero, gdy walka się skończyła: wynik i przeliczone ratingi. */
    public boolean ended() {
        return summary != null;
    }
}
