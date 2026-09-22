package dev.adamgrochulski.javamon.app.auth;

import dev.adamgrochulski.javamon.app.auth.AuthDtos.AuthResponse;
import dev.adamgrochulski.javamon.app.persistence.Trainer;
import dev.adamgrochulski.javamon.app.persistence.TrainerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    /**
     * Hash nieistniejącego hasła. Porównujemy z nim, gdy konta nie ma —
     * żeby logowanie na nieistniejący nick trwało tyle samo, co na istniejący.
     * Bez tego różnica czasu odpowiedzi zdradza, które nicki są zajęte.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final TrainerRepository trainers;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthRateLimits rateLimits;

    AuthService(TrainerRepository trainers, PasswordEncoder passwordEncoder, JwtService jwtService,
                AuthRateLimits rateLimits) {
        this.trainers = trainers;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimits = rateLimits;
    }

    @Transactional
    public AuthResponse register(String username, String rawPassword) {
        if (trainers.existsByUsernameIgnoreCase(username)) {
            throw new AuthExceptions.UsernameTaken(username);
        }
        try {
            // Sprawdzenie wyżej obsługuje normalny przypadek, ale między nim
            // a zapisem mieści się drugie żądanie z tym samym nickiem.
            // Rozstrzyga unikalny indeks w bazie — bez tego catcha wyścig
            // kończyłby się pięćsetką zamiast czytelnego konfliktu.
            Trainer trainer = trainers.saveAndFlush(
                    Trainer.registered(username, passwordEncoder.encode(rawPassword)));
            return respond(trainer);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthExceptions.UsernameTaken(username);
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String username, String rawPassword, String ip) {
        // Przed porównaniem hasła: BCrypt kosztuje ~80 ms, więc sam w sobie
        // jest kosztem, którym da się zająć serwer.
        rateLimits.checkLogin(username, ip);

        Trainer trainer = trainers.findByUsernameIgnoreCase(username)
                .filter(Trainer::isActive)
                .filter(candidate -> !candidate.isGuest())
                .orElse(null);

        String hash = trainer == null ? DUMMY_HASH : trainer.getPasswordHash();
        boolean matches = passwordEncoder.matches(rawPassword, hash);

        if (trainer == null || !matches) {
            rateLimits.loginFailed(username, ip);
            throw new AuthExceptions.InvalidCredentials();
        }
        rateLimits.loginSucceeded(username);
        return respond(trainer);
    }

    @Transactional
    public AuthResponse guest(String ip) {
        // Jedyny endpoint zapisujący do bazy bez uwierzytelnienia.
        rateLimits.checkGuest(ip);

        String username = "Gość-" + UUID.randomUUID().toString().substring(0, 8);
        AuthResponse response = respond(trainers.save(Trainer.guest(username)));
        rateLimits.guestCreated(ip);
        return response;
    }

    private AuthResponse respond(Trainer trainer) {
        return new AuthResponse(jwtService.issue(trainer), trainer.getUsername(), trainer.isGuest());
    }
}
