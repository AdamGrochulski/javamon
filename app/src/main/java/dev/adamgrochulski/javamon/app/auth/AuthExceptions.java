package dev.adamgrochulski.javamon.app.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class AuthExceptions {

    private AuthExceptions() {
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class UsernameTaken extends RuntimeException {
        public UsernameTaken(String username) {
            super("Nick jest już zajęty: " + username);
        }
    }

    /**
     * Jeden wyjątek na "nie ma takiego konta" i "złe hasło" — z tym samym
     * komunikatem. Rozróżnienie ich w odpowiedzi pozwoliłoby sprawdzać,
     * które nicki istnieją.
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class InvalidCredentials extends RuntimeException {
        public InvalidCredentials() {
            super("Nieprawidłowy nick lub hasło");
        }
    }

    /** Limit prób. Komunikat nie zdradza, który licznik się przepełnił. */
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public static class TooManyAttempts extends RuntimeException {
        public TooManyAttempts(String message) {
            super(message);
        }
    }
}
