package dev.adamgrochulski.javamon.engine;

public record Move(
        String name, Type type, MoveCategory category,
        int power, int accuracy, int pp) {

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
    }

}
