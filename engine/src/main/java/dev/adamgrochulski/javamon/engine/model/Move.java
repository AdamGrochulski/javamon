package dev.adamgrochulski.javamon.engine.model;

import java.util.List;

/**
 * Niezmienny szablon ruchu z dexu. Pozostałe PP w trakcie walki
 * żyją osobno (przy BattlePokemon) — tu jest tylko definicja.
 * Efekty uboczne (status, zmiana statów, hazardy...) trzyma lista {@link MoveEffect}.
 */
public record Move(
        String name, Type type, MoveCategory category,
        int power, int accuracy, int pp, int priority,
        List<MoveEffect> effects) {

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
        if (effects == null) {
            throw new IllegalArgumentException("effects nie może być null (użyj pustej listy)");
        }
        effects = List.copyOf(effects);   // niezmienna kopia obronna
    }

    // Wygodny konstruktor: ruch bez efektów ubocznych.
    // Deleguje do kanonicznego, więc istniejące wywołania 7-argumentowe działają bez zmian.
    public Move(String name, Type type, MoveCategory category,
                int power, int accuracy, int pp, int priority) {
        this(name, type, category, power, accuracy, pp, priority, List.of());
    }

    // Wygodny konstruktor: pojedynczy status nakładany na przeciwnika w 100%
    // (Toxic, Will-O-Wisp itd.). Zachowuje stare wywołania z argumentem StatusCondition.
    public Move(String name, Type type, MoveCategory category,
                int power, int accuracy, int pp, int priority, StatusCondition inflictedStatus) {
        this(name, type, category, power, accuracy, pp, priority,
                inflictedStatus == null
                        ? List.of()
                        : List.of(new MoveEffect.InflictStatus(inflictedStatus, MoveEffect.Target.OPPONENT, 100)));
    }

}
