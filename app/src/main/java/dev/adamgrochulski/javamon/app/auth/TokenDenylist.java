package dev.adamgrochulski.javamon.app.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Unieważnione tokeny, po identyfikatorze jti. Wpis żyje dokładnie tyle, ile
 * zostało tokenowi do wygaśnięcia - dłuższe trzymanie niczego nie chroni,
 * bo po tym czasie token i tak nie przejdzie weryfikacji.
 * <p>
 * Cena bezstanowości jest tu widoczna: sprawdzenie kosztuje jedno zapytanie
 * do Redisa na żądanie. Bez tego wylogowanie nie istnieje, a skradziony token
 * żyje do 24 godzin.
 */
@Component
public class TokenDenylist {

    private final StringRedisTemplate redis;

    TokenDenylist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String jti, Instant expiresAt) {
        Duration left = Duration.between(Instant.now(), expiresAt);
        if (left.isNegative() || left.isZero()) {
            return;
        }
        redis.opsForValue().set(key(jti), "1", left);
    }

    public boolean isRevoked(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(key(jti)));
    }

    private static String key(String jti) {
        return "auth:revoked:" + jti;
    }
}
