package dev.adamgrochulski.javamon.app.api;

import dev.adamgrochulski.javamon.app.auth.AuthExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Jedno miejsce zamieniające wyjątki na odpowiedzi. Bez tego @ResponseStatus
 * ustawia kod, ale ciało zostaje puste — front nie ma czego wyświetlić.
 * <p>
 * Dziedziczy po ResponseEntityExceptionHandler, który zna kilkanaście wyjątków
 * Spring MVC (nieznana ścieżka, zła metoda, połamany JSON, zły typ w ścieżce)
 * i przypisuje im właściwe kody. Bez tego catch-all na Exception zamieniał
 * każdy z nich na 500.
 */
@RestControllerAdvice
public class ApiErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    /**
     * Nadpisanie metody rodzica, nie własny @ExceptionHandler — dwa handlery
     * na ten sam typ wyjątku wywalają kontekst przy starcie ("Ambiguous").
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(
                new ApiError("validation_failed", "Żądanie zawiera nieprawidłowe dane", fields));
    }

    /**
     * Wspólne wyjście dla pozostałych wyjątków MVC: rodzic zna właściwy kod
     * (404 nieznana ścieżka, 405 zła metoda, 400 połamany JSON, 415 zły typ),
     * my podmieniamy ciało na nasz kształt. Komunikat jest ogólny — szczegóły
     * wyjątków Springa potrafią zdradzić strukturę API.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        return super.handleExceptionInternal(ex,
                new ApiError("request_failed", "Nieprawidłowe żądanie"),
                headers, status, request);
    }

    @ExceptionHandler(ApiExceptions.InvalidTeam.class)
    ResponseEntity<ApiError> onInvalidTeam(ApiExceptions.InvalidTeam ex) {
        // 422, nie 400: żądanie jest poprawnie zbudowane, ale łamie regułę gry.
        return ResponseEntity.unprocessableEntity().body(
                new ApiError("invalid_team", ex.getMessage()));
    }

    @ExceptionHandler(ApiExceptions.Conflict.class)
    ResponseEntity<ApiError> onConflict(ApiExceptions.Conflict ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiError("conflict", ex.getMessage()));
    }

    @ExceptionHandler(ApiExceptions.NotFound.class)
    ResponseEntity<ApiError> onNotFound(ApiExceptions.NotFound ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ApiError("not_found", ex.getMessage()));
    }

    @ExceptionHandler(AuthExceptions.UsernameTaken.class)
    ResponseEntity<ApiError> onUsernameTaken(AuthExceptions.UsernameTaken ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiError("username_taken", ex.getMessage()));
    }

    @ExceptionHandler(AuthExceptions.InvalidCredentials.class)
    ResponseEntity<ApiError> onInvalidCredentials(AuthExceptions.InvalidCredentials ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiError("invalid_credentials", ex.getMessage()));
    }

    /**
     * Siatka bezpieczeństwa. Komunikat jest stały i bezużyteczny dla atakującego;
     * szczegóły idą wyłącznie do logu. Wyciek nazwy klasy, zapytania SQL albo
     * ścieżki w systemie plików to gotowa mapa dla kogoś, kto szuka wejścia.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> onUnexpected(Exception ex) {
        log.error("Nieobsłużony wyjątek", ex);
        return ResponseEntity.internalServerError().body(
                new ApiError("internal_error", "Coś poszło nie tak"));
    }
}
