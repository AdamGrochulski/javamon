package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.app.persistence.*;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import dev.adamgrochulski.javamon.engine.battle.Player;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;

/** Zakończona walka ląduje w Postgresie: wiersz wyniku, replay i przeliczone ratingi. */
@Component
class JpaBattleArchive implements BattleArchive {

    private final TrainerRepository trainers;
    private final FinishedBattleRepository battles;
    private final BattleReplayRepository replays;
    private final BattleEventJson eventJson;

    JpaBattleArchive(TrainerRepository trainers, FinishedBattleRepository battles,
                     BattleReplayRepository replays, BattleEventJson eventJson) {
        this.trainers = trainers;
        this.battles = battles;
        this.replays = replays;
        this.eventJson = eventJson;
    }

    @Override
    @Transactional
    public BattleSummary archive(BattleSession session, BattleResult result,
                                 Player winner, List<BattleEvent> events) {
        Trainer first = trainer(session, Player.P1);
        Trainer second = trainer(session, Player.P2);

        int beforeFirst = first.getRating();
        int beforeSecond = second.getRating();
        int afterFirst = beforeFirst;
        int afterSecond = beforeSecond;

        // Walka z udziałem gościa nie rusza ratingu: konta gościa powstają bez
        // uwierzytelnienia, więc inaczej ranking dałoby się nabić pętlą curl.
        if (!first.isGuest() && !second.isGuest()) {
            double scoreFirst = winner == null ? 0.5 : (winner == Player.P1 ? 1 : 0);
            afterFirst = Elo.rate(beforeFirst, beforeSecond, scoreFirst);
            afterSecond = Elo.rate(beforeSecond, beforeFirst, 1 - scoreFirst);
            first.applyRating(afterFirst);
            second.applyRating(afterSecond);
        }

        battles.save(new FinishedBattle(session.id(), first, second,
                session.participant(Player.P1).username(), session.participant(Player.P2).username(),
                beforeFirst, beforeSecond, afterFirst, afterSecond,
                winner == null ? null : (winner == Player.P1 ? first : second),
                result, session.battle().getTurn(), session.startedAt(), Instant.now()));

        replays.save(new BattleReplay(session.id(), eventJson.write(events)));

        return new BattleSummary(result, winner,
                Map.of(Player.P1, beforeFirst, Player.P2, beforeSecond),
                Map.of(Player.P1, afterFirst, Player.P2, afterSecond));
    }

    private Trainer trainer(BattleSession session, Player player) {
        UUID id = session.participant(player).trainerId();
        return trainers.findById(id)
                .orElseThrow(() -> new IllegalStateException("Trener " + id + " zniknął w trakcie walki"));
    }
}
