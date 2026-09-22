package dev.adamgrochulski.javamon.app.battle;

import java.util.UUID;

/** Trener zamrożony na czas walki. Zmiana nicka czy ratingu nie przepisuje trwającej walki. */
public record Participant(UUID trainerId, String username, int rating) {
}
