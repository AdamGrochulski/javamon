package dev.adamgrochulski.javamon.engine;

// Bazowe staty gatunku z dexu (niezmienne). Staty przeliczone na poziom
// liczy BattleStats — tego rekordu nie mieszamy z wartościami walki.
public record Stats(
        int hp,
        int attack, int defense,
        int specialAttack, int specialDefense,
        int speed) {

    public Stats {
        requirePositive(hp, "hp");
        requirePositive(attack, "attack");
        requirePositive(defense, "defense");
        requirePositive(specialAttack, "specialAttack");
        requirePositive(specialDefense, "specialDefense");
        requirePositive(speed, "speed");
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "musi być dodatnie, było: " + value);
        }
    }
}