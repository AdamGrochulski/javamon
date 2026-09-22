package dev.adamgrochulski.javamon.app.battle;

import java.util.List;

/**
 * Podpowiedź do renderowania przycisków, nie autoryzacja. Serwer waliduje
 * przysłaną akcję od zera, tak jakby tej listy nigdy nie wysłał.
 */
public record LegalActions(List<LegalMove> moves, List<Integer> switches) {

    /** {@code reason} wypełnione tylko wtedy, gdy ruch jest zablokowany. */
    public record LegalMove(int index, String name, int pp, boolean usable, String reason) {
    }
}
