package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.app.persistence.Team;
import dev.adamgrochulski.javamon.app.persistence.TeamRepository;
import dev.adamgrochulski.javamon.app.persistence.Trainer;
import dev.adamgrochulski.javamon.app.persistence.TrainerRepository;
import dev.adamgrochulski.javamon.app.ws.WsErrorCode;
import dev.adamgrochulski.javamon.app.ws.WsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;

/** Kolejka oczekujących. MVP: jedna lista w pamięci, bez ratingu i bez widełek. */
@Service
public class Matchmaker {

    private record Waiting(Participant participant, Team team) {
    }

    private final Deque<Waiting> queue = new ArrayDeque<>();

    private final TeamRepository teams;
    private final TrainerRepository trainers;
    private final BattleSessionService sessions;

    Matchmaker(TeamRepository teams, TrainerRepository trainers, BattleSessionService sessions) {
        this.teams = teams;
        this.trainers = trainers;
        this.sessions = sessions;
    }

    /** Gotowa walka, gdy było z kim sparować; pusty Optional oznacza czekanie w kolejce. */
    @Transactional(readOnly = true)
    public Optional<BattleSession> join(UUID trainerId, UUID teamId) {
        Team team = teams.findByIdAndTrainerId(teamId, trainerId)
                .orElseThrow(() -> new WsException(WsErrorCode.TEAM_INVALID, "Nie ma takiej drużyny"));
        Trainer trainer = trainers.findById(trainerId)
                .orElseThrow(() -> new WsException(WsErrorCode.INTERNAL_ERROR, "Konto zniknęło"));

        // Sloty ciągnięte jeszcze w transakcji: w kolejce encja jest już odłączona.
        team.getSlots();

        Waiting waiting = new Waiting(
                new Participant(trainerId, trainer.getUsername(), trainer.getRating()), team);

        synchronized (queue) {
            // Ponowne dołączenie zastępuje stary wpis, zamiast tworzyć drugi na to samo konto.
            queue.removeIf(entry -> entry.participant().trainerId().equals(trainerId));

            Waiting opponent = queue.poll();
            if (opponent == null) {
                queue.add(waiting);
                return Optional.empty();
            }
            // Czekający dłużej dostaje P1, czyli przewagę tylko przy remisie szybkości.
            return Optional.of(sessions.create(
                    opponent.participant(), opponent.team(), waiting.participant(), waiting.team()));
        }
    }

    /** Wołane też przy zerwaniu połączenia, inaczej w kolejce zostaje duch. */
    public void leave(UUID trainerId) {
        synchronized (queue) {
            queue.removeIf(entry -> entry.participant().trainerId().equals(trainerId));
        }
    }
}
