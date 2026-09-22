package dev.adamgrochulski.javamon.app.api;

import dev.adamgrochulski.javamon.app.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** Historia walk, replaye i ranking. Same odczyty, więc wszystko readOnly. */
@Service
public class BattleHistoryService {

    private final FinishedBattleRepository battles;
    private final BattleReplayRepository replays;
    private final TrainerRepository trainers;

    BattleHistoryService(FinishedBattleRepository battles, BattleReplayRepository replays,
                         TrainerRepository trainers) {
        this.battles = battles;
        this.replays = replays;
        this.trainers = trainers;
    }

    @Transactional(readOnly = true)
    public List<BattleDtos.HistoryEntry> history(UUID trainerId) {
        return battles.findByPlayer1IdOrPlayer2IdOrderByFinishedAtDesc(trainerId, trainerId).stream()
                .map(battle -> BattleDtos.HistoryEntry.of(battle, trainerId))
                .toList();
    }

    /** Replay jest publiczny po UUID: kto ma link, ten ogląda. Id jest nieodgadywalne. */
    @Transactional(readOnly = true)
    public BattleDtos.Replay replay(UUID battleId) {
        FinishedBattle battle = battles.findById(battleId)
                .orElseThrow(() -> new ApiExceptions.NotFound("Nie ma takiej walki"));
        BattleReplay replay = replays.findById(battleId)
                .orElseThrow(() -> new ApiExceptions.NotFound("Ta walka nie ma zapisanego replaya"));

        String winner = null;
        if (battle.getWinner() != null) {
            winner = battle.getWinner().getId().equals(battle.getPlayer1().getId())
                    ? battle.getPlayer1Name()
                    : battle.getPlayer2Name();
        }

        return new BattleDtos.Replay(battle.getId(), battle.getPlayer1Name(), battle.getPlayer2Name(),
                winner, battle.getResult(), battle.getTurns(), battle.getFinishedAt(), replay.getEvents());
    }

    @Transactional(readOnly = true)
    public List<BattleDtos.LeaderboardEntry> leaderboard() {
        List<Trainer> top = trainers.findTop20ByGuestFalseAndDeletedAtIsNullOrderByRatingDesc();
        return IntStream.range(0, top.size())
                .mapToObj(i -> new BattleDtos.LeaderboardEntry(i + 1, top.get(i).getUsername(), top.get(i).getRating()))
                .toList();
    }
}
