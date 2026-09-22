package dev.adamgrochulski.javamon.app.battle;

/** Ranking ELO, K=32. Czysta arytmetyka, bez stanu i bez zależności. */
public final class Elo {

    public static final int K = 32;
    public static final int START = 1000;

    private Elo() {
    }

    /**
     * Nowy rating gracza po walce.
     *
     * @param score 1 wygrana, 0 przegrana, 0.5 remis
     */
    public static int rate(int rating, int opponentRating, double score) {
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentRating - rating) / 400.0));
        return (int) Math.round(rating + K * (score - expected));
    }
}
