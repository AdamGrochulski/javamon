package dev.adamgrochulski.javamon.app.ws;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Ramka wychodząca. {@code seq} niosą wyłącznie ramki związane z konkretną
 * walką — AUTH_OK, ERROR i PONG istnieją poza nią i numeru nie mają.
 * NON_NULL dotyczy tylko tej koperty; eventy w środku serializują się
 * z domyślnym ustawieniem, bo tam null bywa znaczący (BATTLE_END.winner).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerFrame(String type, Long seq, Object payload) {
}
