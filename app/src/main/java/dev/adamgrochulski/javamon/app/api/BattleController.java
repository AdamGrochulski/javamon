package dev.adamgrochulski.javamon.app.api;

import dev.adamgrochulski.javamon.app.auth.AuthenticatedTrainer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class BattleController {

    private final BattleHistoryService history;

    BattleController(BattleHistoryService history) {
        this.history = history;
    }

    /** Musi być zadeklarowane przed /{id}, inaczej "history" trafi tam jako UUID. */
    @GetMapping("/api/battles/history")
    public List<BattleDtos.HistoryEntry> history(@AuthenticationPrincipal AuthenticatedTrainer trainer) {
        return history.history(trainer.id());
    }

    @GetMapping("/api/battles/{battleId}")
    public BattleDtos.Replay replay(@PathVariable UUID battleId) {
        return history.replay(battleId);
    }

    @GetMapping("/api/leaderboard")
    public List<BattleDtos.LeaderboardEntry> leaderboard() {
        return history.leaderboard();
    }
}
