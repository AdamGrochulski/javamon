package dev.adamgrochulski.javamon.app.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Liczniki w Redisie, nie w pamięci: przy dwóch instancjach limit liczony
 * lokalnie byłby dwa razy luźniejszy, a po restarcie zerowałby się sam.
 */
@Component
public class AuthRateLimits {

    private final StringRedisTemplate redis;
    private final SecurityLimits limits;

    AuthRateLimits(StringRedisTemplate redis, SecurityLimits limits) {
        this.redis = redis;
        this.limits = limits;
    }

    /**
     * Sprawdzane przed porównaniem hasła. Oba liczniki są potrzebne: limit po samym
     * adresie obchodzi się botnetem, a limit po samym nicku pozwala zablokować
     * komuś konto na złość.
     */
    public void checkLogin(String username, String ip) {
        if (count(userKey(username)) >= limits.loginFailuresPerUser()
                || count(ipKey(ip)) >= limits.loginFailuresPerIp()) {
            throw new AuthExceptions.TooManyAttempts("Za dużo prób logowania, spróbuj później");
        }
    }

    public void loginFailed(String username, String ip) {
        bump(userKey(username), limits.loginWindow());
        bump(ipKey(ip), limits.loginWindow());
    }

    /** Udane logowanie zeruje licznik konta, ale nie adresu: skan po nickach ma dalej boleć. */
    public void loginSucceeded(String username) {
        redis.delete(userKey(username));
    }

    public void checkGuest(String ip) {
        if (count(guestKey(ip)) >= limits.guestsPerIp()) {
            throw new AuthExceptions.TooManyAttempts("Za dużo kont gościa z tego adresu");
        }
    }

    public void guestCreated(String ip) {
        bump(guestKey(ip), limits.guestWindow());
    }

    private long count(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    // TTL ustawiany tylko przy pierwszym trafieniu, żeby okno nie przesuwało się w nieskończoność.
    private void bump(String key, Duration window) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, window);
        }
    }

    private static String userKey(String username) {
        return "auth:fail:user:" + username.toLowerCase(Locale.ROOT);
    }

    private static String ipKey(String ip) {
        return "auth:fail:ip:" + ip;
    }

    private static String guestKey(String ip) {
        return "auth:guest:ip:" + ip;
    }
}
