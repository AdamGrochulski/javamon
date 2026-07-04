package dev.adamgrochulski.javamon.engine.model;

/**
 * Niezmienny szablon ruchu z dexu. Pozostałe PP w trakcie walki
 * żyją osobno (przy BattlePokemon) — tu jest tylko definicja.
 */
public record Move(
        String name, Type type, MoveCategory category,
        int power, int accuracy, int pp, int priority) {

    // Walidacja przy tworzeniu — nielegalny ruch nie powstanie.
    public Move {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nazwa ruchu nie może być pusta");
        }
        if (power < 0) {
            throw new IllegalArgumentException("power nie może być ujemne, było: " + power);
        }
        if (accuracy < 1 || accuracy > 100) {
            throw new IllegalArgumentException("accuracy musi być w 1..100, było: " + accuracy);
        }
        if (pp <= 0) {
            throw new IllegalArgumentException("pp musi być dodatnie, było: " + pp);
        }
        if (category == MoveCategory.STATUS && power != 0) {
            throw new IllegalArgumentException("ruch STATUS nie może mieć power, było: " + power);
        }
        if (priority < -7 || priority > 5) {
            throw new IllegalArgumentException("priority musi być w -7..5, było: " + priority);
        }
    }

}
