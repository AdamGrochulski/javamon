package dev.adamgrochulski.javamon.engine.model;

/**
 * Staty przeliczone na konkretny poziom (Stats = bazowe z dexu).
 * hp bazowe staje się maxHp; reszta statów wg wzoru walki.
 */
public record BattleStats(
        int maxHp,
        int attack, int defense,
        int specialAttack, int specialDefense,
        int speed) {

    public BattleStats {
        requirePositive(maxHp, "maxHp");
        requirePositive(attack, "attack");
        requirePositive(defense, "defense");
        requirePositive(specialAttack, "specialAttack");
        requirePositive(specialDefense, "specialDefense");
        requirePositive(speed, "speed");
    }

    public static BattleStats fromBase(Stats base, int level) {
        return new BattleStats(
                deriveHp(base.hp(), level),
                deriveStat(base.attack(), level),
                deriveStat(base.defense(), level),
                deriveStat(base.specialAttack(), level),
                deriveStat(base.specialDefense(), level),
                deriveStat(base.speed(), level)
        );
    }

    private static int deriveHp(int hp, int level) {
        return (2 * hp * level) / 100 + level + 10;
    }

    private static int deriveStat(int stat, int level) {
        return (2 * stat * level) / 100 + 5;
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " musi być dodatnie, było: " + value);
        }
    }
}
