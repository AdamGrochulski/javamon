package dev.adamgrochulski.javamon.engine;

public interface Rng {
    // Losowa liczba całkowita w domkniętym przedziale [min, max]
    int nextInt(int minInclusive, int maxInclusive);

    // Metoda domyślna - rzut na procentową szansę (accuracy, itp.)
    default boolean chance(int percent){
        return nextInt(1, 100) <= percent;
    }
}
