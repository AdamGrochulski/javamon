package dev.adamgrochulski.javamon.engine.rng;

/**
 * Wstrzykiwane źródło losowości — klucz determinizmu silnika.
 * Testy dają seeded/fake implementację, produkcja RandomRng.
 */
public interface Rng {
    /** Losowa liczba całkowita w domkniętym przedziale [min, max]. */
    int nextInt(int minInclusive, int maxInclusive);

    /** Rzut na procentową szansę (accuracy, crit itp.). */
    default boolean chance(int percent){
        return nextInt(1, 100) <= percent;
    }
}
