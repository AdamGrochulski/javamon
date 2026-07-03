package dev.adamgrochulski.javamon.engine;

public final class RandomRng implements Rng{
    private final java.util.Random random;

    public RandomRng() { this.random = new java.util.Random(); }
    public RandomRng(long seed) { this.random = new java.util.Random(seed); }

    @Override
    public int nextInt(int minInclusive, int maxInclusive){
        return random.nextInt(minInclusive, maxInclusive+1);
    }
}
