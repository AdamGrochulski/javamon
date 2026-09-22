package dev.adamgrochulski.javamon.app.battle;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.adamgrochulski.javamon.app.persistence.Team;
import dev.adamgrochulski.javamon.app.persistence.TeamRepository;
import dev.adamgrochulski.javamon.app.persistence.Trainer;
import dev.adamgrochulski.javamon.app.persistence.TrainerRepository;
import dev.adamgrochulski.javamon.app.ws.WsErrorCode;
import dev.adamgrochulski.javamon.app.ws.WsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kolejka oczekujących w Redisie: lista identyfikatorów w kolejności zgłoszeń
 * i osobny klucz ze składem każdego czekającego.
 * <p>
 * Lista, a nie zbiór w pamięci: LPOP jest atomowy, więc dwóch graczy dołączających
 * w tej samej chwili nie wyjmie z kolejki tego samego przeciwnika.
 */
@Service
public class Matchmaker {

    private static final String QUEUE = "matchmaking:queue";
    private static final Duration ENTRY_TTL = Duration.ofMinutes(30);

    /** Skład czekającego. Kopiowany przy wejściu do kolejki, więc edycja drużyny nic nie zmienia. */
    record QueueEntry(Participant participant, List<MonSnapshot> roster) {
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final TeamRepository teams;
    private final TrainerRepository trainers;
    private final BattleSessionService sessions;

    Matchmaker(StringRedisTemplate redis, ObjectMapper json, TeamRepository teams,
               TrainerRepository trainers, BattleSessionService sessions) {
        this.redis = redis;
        this.json = json;
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

        QueueEntry entry = new QueueEntry(
                new Participant(trainerId, trainer.getUsername(), trainer.getRating()),
                sessions.rosterOf(team));

        // Ponowne dołączenie zastępuje stary wpis, zamiast czekać dwa razy na to samo konto.
        leave(trainerId);
        write(entryKey(trainerId), entry);

        Optional<QueueEntry> opponent = takeOpponent(trainerId);
        if (opponent.isEmpty()) {
            redis.opsForList().rightPush(QUEUE, trainerId.toString());
            return Optional.empty();
        }

        redis.delete(entryKey(trainerId));
        QueueEntry waiting = opponent.get();
        // Czekający dłużej dostaje P1, czyli przewagę tylko przy remisie szybkości.
        return Optional.of(sessions.create(
                waiting.participant(), waiting.roster(), entry.participant(), entry.roster()));
    }

    /** Wołane też przy zerwaniu połączenia, inaczej w kolejce zostaje duch. */
    public void leave(UUID trainerId) {
        redis.opsForList().remove(QUEUE, 0, trainerId.toString());
        redis.delete(entryKey(trainerId));
    }

    /** Zdejmuje pierwszego czekającego, przeskakując wpisy, które zdążyły wygasnąć. */
    private Optional<QueueEntry> takeOpponent(UUID self) {
        String candidate;
        while ((candidate = redis.opsForList().leftPop(QUEUE)) != null) {
            if (candidate.equals(self.toString())) {
                continue;
            }
            String raw = redis.opsForValue().getAndDelete(entryKey(UUID.fromString(candidate)));
            if (raw != null) {
                return Optional.of(read(raw));
            }
        }
        return Optional.empty();
    }

    private void write(String key, QueueEntry entry) {
        try {
            redis.opsForValue().set(key, json.writeValueAsString(entry), ENTRY_TTL);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Nie da się zapisać wpisu kolejki", ex);
        }
    }

    private QueueEntry read(String raw) {
        try {
            return json.readValue(raw, QueueEntry.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Nieczytelny wpis kolejki", ex);
        }
    }

    private static String entryKey(UUID trainerId) {
        return "matchmaking:entry:" + trainerId;
    }
}
