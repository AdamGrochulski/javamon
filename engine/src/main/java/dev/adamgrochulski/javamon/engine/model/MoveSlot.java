package dev.adamgrochulski.javamon.engine.model;

public final class MoveSlot {
    private final Move move;
    private int remainingPp;

    public MoveSlot(Move move) {
        if(move == null) throw new IllegalArgumentException("move wymagany");
        this.move = move;
        this.remainingPp = move.pp();   // start = max PP
    }

    public Move getMove() { return move; }
    public int getRemainingPp() { return remainingPp; }

    public boolean hasPp() { return remainingPp > 0; }

    public void use() {
        if(hasPp()) remainingPp--;
    }

    /** Odtworzenie PP z zapisu. Pakietowe: z zewnątrz slot pozostaje tylko do użycia. */
    void restore(int pp) {
        if (pp < 0 || pp > move.pp()) {
            throw new IllegalArgumentException("PP poza zakresem 0.." + move.pp() + ": " + pp);
        }
        this.remainingPp = pp;
    }
}
