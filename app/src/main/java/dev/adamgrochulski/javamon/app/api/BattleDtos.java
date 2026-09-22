package dev.adamgrochulski.javamon.app.api;

import com.fasterxml.jackson.annotation.JsonRawValue;
import dev.adamgrochulski.javamon.app.persistence.BattleResult;
import dev.adamgrochulski.javamon.app.persistence.FinishedBattle;

import java.time.Instant;
import java.util.UUID;

public final class BattleDtos {

    private BattleDtos() {
    }

    public record HistoryEntry(UUID battleId, String opponent, boolean won, boolean draw,
                               BattleResult result, int turns, int ratingBefore, int ratingAfter,
                               Instant finishedAt) {

        static HistoryEntry of(FinishedBattle battle, UUID viewer) {
            boolean viewerIsFirst = battle.getPlayer1().getId().equals(viewer);
            UUID winnerId = battle.getWinner() == null ? null : battle.getWinner().getId();

            return new HistoryEntry(
                    battle.getId(),
                    viewerIsFirst ? battle.getPlayer2Name() : battle.getPlayer1Name(),
                    winnerId != null && winnerId.equals(viewer),
                    winnerId == null,
                    battle.getResult(),
                    battle.getTurns(),
                    viewerIsFirst ? battle.getPlayer1RatingBefore() : battle.getPlayer2RatingBefore(),
                    viewerIsFirst ? battle.getPlayer1RatingAfter() : battle.getPlayer2RatingAfter(),
                    battle.getFinishedAt());
        }
    }

    /**
     * Eventy idą surowym JSON-em prosto z kolumny jsonb: są już w formacie protokołu,
     * więc odczytanie ich do obiektów tylko po to, żeby zaraz zapisać z powrotem,
     * byłoby czystą stratą.
     */
    public record Replay(UUID battleId, String player1, String player2, String winner,
                         BattleResult result, int turns, Instant finishedAt,
                         @JsonRawValue String events) {
    }

    public record LeaderboardEntry(int rank, String username, int rating) {
    }
}
