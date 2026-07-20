package dev.adamgrochulski.javamon.engine.model;

import java.util.Set;

/**
 * Gatunek z Pokédexu — dane niezmienne, wspólne dla wszystkich egzemplarzy.
 * Konkretny Pokémon w walce (poziom, przeliczone staty, HP, status) to BattlePokemon.
 *
 * @param learnset nazwy ruchów legalnych dla gatunku; gwarantowane, że wszystkie
 *                 istnieją w MoveDex (generator filtruje po naszym moves.json)
 */
public record Species(
        String id,
        String name,
        int num,
        Type primary,
        Type secondary,
        Stats base,
        Set<String> learnset) {

    public Species {
        if(id == null || id.isBlank()) {
            throw new IllegalArgumentException("id gatunku jest wymagane");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nazwa gatunku jest wymagana");
        }
        if (primary == null) {
            throw new IllegalArgumentException(name + ": typ podstawowy jest wymagany");
        }
        if (primary == secondary) {
            throw new IllegalArgumentException(name + ": typ drugi nie może być taki sam jak pierwszy");
        }
        if (base == null) {
            throw new IllegalArgumentException(name + ": brak bazowych statów");
        }
        learnset = learnset == null ? Set.of() : Set.copyOf(learnset);
    }

    public boolean canLearn(String moveName) {
        return learnset.contains(moveName);
    }

    public boolean hasType(Type type) {
        return primary == type || secondary == type;
    }
}
