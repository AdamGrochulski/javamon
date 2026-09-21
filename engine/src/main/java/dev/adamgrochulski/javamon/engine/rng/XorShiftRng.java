package dev.adamgrochulski.javamon.engine.rng;

/**
 * RNG z jawnym stanem: jedna liczba, którą da się zapisać i odtworzyć.
 * java.util.Random swojego stanu nie wystawia, więc walki nie da się wznowić.
 */
public final class XorShiftRng implements Rng {

    private long state;

    /** @param seed ziarno nowej walki albo stan odczytany z zapisu */
    public XorShiftRng(long seed) {
        // Zero jest punktem stałym xorshifta.
        this.state = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
    }

    /** Stan do zapisania między turami. Podaj go z powrotem konstruktorowi. */
    public long state() {
        return state;
    }

    @Override
    public int nextInt(int minInclusive, int maxInclusive) {
        if (minInclusive > maxInclusive) {
            throw new IllegalArgumentException(
                    "pusty przedział: [" + minInclusive + ", " + maxInclusive + "]");
        }
        long span = (long) maxInclusive - minInclusive + 1;
        // floorMod, bo nextLong() bywa ujemne.
        return (int) (minInclusive + Math.floorMod(nextLong(), span));
    }

    private long nextLong() {
        long x = state;
        x ^= x >>> 12;
        x ^= x << 25;
        x ^= x >>> 27;
        state = x;
        return x * 0x2545F4914F6CDD1DL;
    }
}
