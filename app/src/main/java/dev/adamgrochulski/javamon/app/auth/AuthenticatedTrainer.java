package dev.adamgrochulski.javamon.app.auth;

import java.util.UUID;

/**
 * Tożsamość wyjęta z tokenu. Trafia jako principal do SecurityContext,
 * więc kontrolery dostają ją przez @AuthenticationPrincipal — bez zaglądania
 * do bazy tylko po to, żeby wiedzieć, kto pyta.
 */
public record AuthenticatedTrainer(UUID id, String username, boolean guest) {
}
