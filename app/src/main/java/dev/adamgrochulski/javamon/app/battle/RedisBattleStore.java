package dev.adamgrochulski.javamon.app.battle;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Trwające walki w Redisie. Dokument na walkę, TTL sprząta to, czego nikt nie dokończył. */
@Component
public class RedisBattleStore implements BattleStore {

    private static final Duration TTL = Duration.ofHours(2);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration LOCK_WAIT = Duration.ofSeconds(3);
    private static final long RETRY_MILLIS = 20;

    /** Zwolnienie zamka tylko wtedy, gdy nadal jest nasz - inaczej kasujemy cudzy po swoim TTL. */
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final BattleEventJson eventJson;

    RedisBattleStore(StringRedisTemplate redis, ObjectMapper json, BattleEventJson eventJson) {
        this.redis = redis;
        this.json = json;
        this.eventJson = eventJson;
    }

    @Override
    public Optional<BattleSnapshot> load(UUID battleId) {
        String raw = redis.opsForValue().get(key(battleId));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(raw, BattleSnapshot.class));
        } catch (JacksonException ex) {
            throw new IllegalStateException("Nieczytelny zapis walki " + battleId, ex);
        }
    }

    @Override
    public void save(BattleSnapshot snapshot) {
        try {
            redis.opsForValue().set(key(snapshot.id()), json.writeValueAsString(snapshot), TTL);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Nie da się zapisać walki " + snapshot.id(), ex);
        }
    }

    @Override
    public void appendEvents(UUID battleId, List<BattleEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        String key = eventsKey(battleId);
        // RPUSH, a nie przepisanie całej listy: dopisanie jest O(1) niezależnie od długości walki.
        redis.opsForList().rightPushAll(key, events.stream().map(eventJson::writeOne).toList());
        redis.expire(key, TTL);
    }

    @Override
    public List<BattleEvent> events(UUID battleId) {
        List<String> raw = redis.opsForList().range(eventsKey(battleId), 0, -1);
        return raw == null ? List.of() : raw.stream().map(eventJson::readOne).toList();
    }

    @Override
    public <T> T locked(UUID battleId, Supplier<T> action) {
        String lockKey = "battle:lock:" + battleId;
        String token = UUID.randomUUID().toString();

        if (!acquire(lockKey, token)) {
            throw new IllegalStateException("Nie udało się zająć walki " + battleId);
        }
        try {
            return action.get();
        } finally {
            redis.execute(RELEASE, List.of(lockKey), token);
        }
    }

    private boolean acquire(String lockKey, String token) {
        long deadline = System.nanoTime() + LOCK_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL))) {
                return true;
            }
            try {
                Thread.sleep(RETRY_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static String key(UUID battleId) {
        return "battle:" + battleId;
    }

    private static String eventsKey(UUID battleId) {
        return "battle:events:" + battleId;
    }
}
