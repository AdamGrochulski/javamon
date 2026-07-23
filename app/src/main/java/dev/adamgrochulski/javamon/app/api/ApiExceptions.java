package dev.adamgrochulski.javamon.app.api;

public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** Zasób nie istnieje albo nie należy do wołającego — celowo nierozróżnialne. */
    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** Żądanie poprawne składniowo, ale łamie regułę domeny. */
    public static class InvalidTeam extends RuntimeException {
        public InvalidTeam(String message) {
            super(message);
        }
    }

    /** Zderzenie z istniejącym zasobem — np. drugi raz ta sama nazwa drużyny. */
    public static class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }
}
