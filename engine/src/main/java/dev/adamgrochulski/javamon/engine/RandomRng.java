package dev.adamgrochulski.javamon.engine;

// Produkcyjny RNG. Konstruktor z seedem = powtarzalne walki (replay, testy).
public final class RandomRng implements Rng{
    private final java.util.Random random;

    public RandomRng() { this.random = new java.util.Random(); }
    public RandomRng(long seed) { this.random = new java.util.Random(seed); }

    @Override
    public int nextInt(int minInclusive, int maxInclusive){
        // Random.nextInt bierze przedział półotwarty [min, max), stąd +1 na domknięcie.
        return random.nextInt(minInclusive, maxInclusive+1);
    }
}
