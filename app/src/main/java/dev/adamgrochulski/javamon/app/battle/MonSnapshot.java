package dev.adamgrochulski.javamon.app.battle;

import java.util.List;

/** Niezmienna tożsamość Pokémona w walce. Stan bojowy trzyma Battle.State. */
public record MonSnapshot(String speciesId, int level, List<String> moves) {
}
