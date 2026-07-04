package dev.adamgrochulski.javamon.engine;

// Czysta funkcja liczenia obrażeń — nie mutuje niczego (takeDamage woła resolver wyżej).
// TypeChart i Rng wstrzykiwane, więc test daje fake RNG i liczy wynik deterministycznie.
public final class DamageCalculator {

    private static final int CRIT_CHANCE_PERCENT = 6;
    private DamageCalculator() {}   // klasa narzędziowa, brak instancji

    public static DamageResult calculate(BattlePokemon attacker, BattlePokemon defender, Move move, TypeChart chart, Rng rng){
        if(move.category() == MoveCategory.STATUS){
            // ruch niedamage'owy: 0 obrażeń, ale effectiveness 1.0 (to nie immunity)
            return new DamageResult(0, false, 1.0);
        }

        int atk = attacker.getAttack();
        int def = defender.getDefense();
        if(move.category() == MoveCategory.SPECIAL){
            atk = attacker.getSpecialAttack();
            def = defender.getSpecialDefense();
        }

        int base = (2* attacker.getLevel()/5 +2) * move.power() * atk / def / 50 + 2;

        double stab = (move.type() == attacker.getPrimary() || move.type() == attacker.getSecondary() ) ? 1.5 : 1.0;

        double typeEff = chart.multiplier(move.type(), defender.getPrimary()) * (defender.getSecondary() != null ? chart.multiplier(move.type(), defender.getSecondary()) : 1.0 );

        // Kolejność RNG stała: najpierw crit, potem random. Testy zależą od tej sekwencji.
        boolean crit = rng.chance(CRIT_CHANCE_PERCENT);
        double critMult = crit ? 1.5 : 1.0;

        double random = rng.nextInt(85,100) / 100.0;

        // Rzut (int) na końcu = floor (damage nieujemny).
        int damage = (int) (base * stab * typeEff * critMult * random);

        return new DamageResult(damage, crit, typeEff);
    }
}
