package dev.adamgrochulski.javamon.engine;

public class Battle {
    private final BattleSide side1;     // P1
    private final BattleSide side2;     // P2
    private final Rng rng;
    private final TypeChart chart;
    private int turn;

    public Battle(BattleSide side1, BattleSide side2, Rng rng, TypeChart chart) {
        if(side1 == null) throw new IllegalArgumentException("side1 jest wymagany");
        if(side2 == null) throw new IllegalArgumentException("side2 jest wymagany");
        if(rng == null) throw new IllegalArgumentException("rng jest wymagany");
        if(chart == null) throw new IllegalArgumentException("chart jest wymagany");

        this.side1 = side1;
        this.side2 = side2;
        this.rng = rng;
        this.chart = chart;

        turn = 1;       // Pierwsza tura
    }

    public BattleSide side(Player p) {
        return p == Player.P1 ? side1 : side2;
    }

    public Rng getRng() { return rng; }
    public TypeChart getChart() { return chart; }
    public int getTurn() { return turn; }

    public void nextTurn() { turn++; }
    public boolean isOver() { return side1.isDefeated() || side2.isDefeated(); }

    public Player winner() {
        boolean p1Dead = side1.isDefeated();
        boolean p2Dead = side2.isDefeated();

        if(p1Dead && p2Dead) return null;
        if(p2Dead) return Player.P1;
        if(p1Dead) return Player.P2;
        return null;
    }
}
