package dev.adamgrochulski.javamon.engine.damage;

import dev.adamgrochulski.javamon.engine.model.BattlePokemon;
import dev.adamgrochulski.javamon.engine.model.Move;
import dev.adamgrochulski.javamon.engine.model.MoveCategory;
import dev.adamgrochulski.javamon.engine.model.StatusCondition;
import dev.adamgrochulski.javamon.engine.model.Type;
import dev.adamgrochulski.javamon.engine.model.Weather;
import dev.adamgrochulski.javamon.engine.rng.Rng;

/**
 * Czysta funkcja liczenia obrażeń — nie mutuje niczego (takeDamage woła resolver wyżej).
 * TypeChart i Rng wstrzykiwane, więc test daje fake RNG i liczy wynik deterministycznie.
 */
public final class DamageCalculator {

    private static final int CRIT_CHANCE_PERCENT = 6;

    private DamageCalculator() {
    }   // klasa narzędziowa, brak instancji

    // Wariant bez pogody — zachowuje istniejące wywołania/testy (NONE = brak modyfikatora).
    public static DamageResult calculate(BattlePokemon attacker, BattlePokemon defender, Move move, TypeChart chart, Rng rng) {
        return calculate(attacker, defender, move, chart, rng, Weather.NONE);
    }

    public static DamageResult calculate(BattlePokemon attacker, BattlePokemon defender, Move move, TypeChart chart, Rng rng, Weather weather) {
        if (move.category() == MoveCategory.STATUS) {
            // ruch niedamage'owy: 0 obrażeń, ale effectiveness 1.0 (to nie immunity)
            return new DamageResult(0, false, 1.0);
        }

        // Staty efektywne = po nałożeniu stopni boostów/debuffów (stage).
        int atk = attacker.getEffectiveAttack();
        int def = defender.getEffectiveDefense();
        if (move.category() == MoveCategory.SPECIAL) {
            atk = attacker.getEffectiveSpecialAttack();
            def = defender.getEffectiveSpecialDefense();
        }

        if (attacker.getStatus() == StatusCondition.BRN && move.category() == MoveCategory.PHYSICAL) {
            atk = atk / 2;
        }

        int base = (2 * attacker.getLevel() / 5 + 2) * move.power() * atk / def / 50 + 2;

        double stab = (move.type() == attacker.getPrimary() || move.type() == attacker.getSecondary()) ? 1.5 : 1.0;

        double typeEff = chart.multiplier(move.type(), defender.getPrimary()) * (defender.getSecondary() != null ? chart.multiplier(move.type(), defender.getSecondary()) : 1.0);

        // Kolejność RNG stała: najpierw crit, potem random. Testy zależą od tej sekwencji.
        boolean crit = rng.chance(CRIT_CHANCE_PERCENT);
        double critMult = crit ? 1.5 : 1.0;

        double random = rng.nextInt(85, 100) / 100.0;

        double weatherMult = weatherMultiplier(weather, move.type());

        // Rzut (int) na końcu = floor (damage nieujemny).
        int damage = (int) (base * stab * typeEff * critMult * weatherMult * random);

        return new DamageResult(damage, crit, typeEff);
    }

    // RAIN wzmacnia Water (1.5) i tłumi Fire (0.5); SUN odwrotnie. Reszta bez zmian.
    private static double weatherMultiplier(Weather weather, Type moveType) {
        return switch (weather) {
            case RAIN -> moveType == Type.WATER ? 1.5 : moveType == Type.FIRE ? 0.5 : 1.0;
            case SUN -> moveType == Type.FIRE ? 1.5 : moveType == Type.WATER ? 0.5 : 1.0;
            case NONE, SANDSTORM, SNOW -> 1.0;
        };
    }
}
