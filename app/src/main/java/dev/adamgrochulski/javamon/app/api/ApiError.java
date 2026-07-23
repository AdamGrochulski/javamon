package dev.adamgrochulski.javamon.app.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Jednolite ciało odpowiedzi błędu. Pole {@code fields} pojawia się w JSON-ie
 * tylko przy błędach walidacji — front dostaje mapę pole → komunikat i wie,
 * co podświetlić w formularzu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String message, Map<String, String> fields) {

    public ApiError(String error, String message) {
        this(error, message, null);
    }
}
