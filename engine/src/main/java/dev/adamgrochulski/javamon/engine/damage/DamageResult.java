package dev.adamgrochulski.javamon.engine.damage;

/**
 * Wynik pojedynczego liczenia obrażeń — pod eventy walki.
 * effectiveness = złożony mnożnik typów (po obu typach obrońcy);
 * 0.0 oznacza immunity, stąd osobny sygnał noEffect().
 */
public record DamageResult(int damage, boolean crit, double effectiveness) {

    public boolean noEffect() {
        return effectiveness == 0.0;
    }
}
