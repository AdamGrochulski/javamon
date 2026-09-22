package dev.adamgrochulski.javamon.app.auth;

import dev.adamgrochulski.javamon.app.persistence.Trainer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Wystawianie i weryfikacja tokenów. Payload jest podpisany, ale jawny
 * (Base64URL) — nie trafia do niego nic poufnego ani nic zmiennego.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final Duration guestTtl;
    private final TokenDenylist denylist;

    JwtService(JwtProperties properties, TokenDenylist denylist) {
        this.denylist = denylist;
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException(
                    "Brak JWT_SECRET. Ustaw zmienną środowiskową — bez klucza "
                            + "podpisu aplikacja nie ma prawa wstać.");
        }
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET musi mieć co najmniej " + MIN_SECRET_BYTES
                            + " bajtów, ma " + secret.length);
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = properties.ttl();
        this.guestTtl = properties.guestTtl();
    }

    public String issue(Trainer trainer) {
        Instant now = Instant.now();
        Duration lifetime = trainer.isGuest() ? guestTtl : ttl;
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(trainer.getId().toString())
                .claim("name", trainer.getUsername())
                .claim("guest", trainer.isGuest())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    /** Unieważnia okazany token. Cicho przechodzi, gdy token i tak jest nieważny. */
    public void revoke(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            denylist.revoke(claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            // Wylogowanie nieważnym tokenem to nie błąd: efekt jest ten sam.
        }
    }

    /**
     * Pusty Optional dla każdego powodu odrzucenia — wygaśnięcie, zły podpis,
     * śmieci zamiast tokenu. Wołający nie potrzebuje tej różnicy, a odpowiedź
     * mówiąca "podpis zły, ale format dobry" pomaga wyłącznie atakującemu.
     */
    public Optional<AuthenticatedTrainer> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (denylist.isRevoked(claims.getId())) {
                return Optional.empty();
            }

            return Optional.of(new AuthenticatedTrainer(
                    UUID.fromString(claims.getSubject()),
                    claims.get("name", String.class),
                    Boolean.TRUE.equals(claims.get("guest", Boolean.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
