package dev.adamgrochulski.javamon.engine;

import java.util.List;
import java.util.Objects;

/**
 * Instancja Pokémona na polu walki. Klasa, nie record — bo currentHp
 * zmienia się w trakcie walki. Reszta (base, typy, level, derived) jest stała.
 */
public class BattlePokemon {
    private final String name;
    private final Stats base;
    private final Type primary;
    private final Type secondary;   // null = jednotypowy
    private final int level;
    private StatusCondition status = StatusCondition.NONE;
    private int statusCounter;

    // Staty przeliczone na level (liczone raz w konstruktorze).
    private final BattleStats derived;
    private final List<MoveSlot> moves;

    // Jedyny stan zmienny na tym etapie — spada od obrażeń.
    private int currentHp;

    public BattlePokemon(String name, Stats base, Type primary, Type secondary, int level, List<Move> moves){
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name nie może być puste");
        }
        this.name = name;

        if (level < 1 || level > 100) {
            throw new IllegalArgumentException("level musi być w zakresie 1..100, było: " + level);
        }
        this.level = level;

        if (primary == null) {
            throw new IllegalArgumentException("primary jest wymagany");
        }
        if (secondary != null && secondary == primary) {
            throw new IllegalArgumentException("secondary nie może być równe primary: " + primary);
        }
        this.primary = primary;
        this.secondary = secondary;

        if (base == null) {
            throw new IllegalArgumentException("base (Stats) jest wymagane");
        }
        this.base = base;

        this.derived = BattleStats.fromBase(base, level);

        currentHp = derived.maxHp();

        if(moves == null) {
            throw new IllegalArgumentException("moveset jest wymagany");
        }

        if(moves.isEmpty()) {
            throw new IllegalArgumentException("moveset musi mieć co najmniej 1 ruch");
        }

        if(moves.size() > 4) {
            throw new IllegalArgumentException("moveset: max 4 ruchy, było: " + moves.size());
        }

        if(moves.stream()
                .anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("moveset nie może zawierać null");
        }

        List<MoveSlot> slots = moves.stream()
                .map(MoveSlot::new).toList();

        this.moves = List.copyOf(slots);
    }

    public String getName() { return name; }

    public Type getPrimary() { return primary; }

    public Type getSecondary() { return secondary; }

    public int getLevel() { return level; }

    public int getMaxHp() { return derived.maxHp(); }

    public int getCurrentHp() { return currentHp; }

    public int getAttack() { return derived.attack(); }

    public int getDefense() { return derived.defense(); }

    public int getSpecialAttack() { return derived.specialAttack(); }

    public int getSpecialDefense() { return derived.specialDefense(); }

    public int getSpeed() { return derived.speed(); }

    public StatusCondition getStatus() { return status; }

    public int getStatusCounter() { return statusCounter; }

    public boolean isFainted() { return currentHp <= 0; }

    // Obcięcie do 0 — HP nie schodzi poniżej zera.
    public void takeDamage(int dmg) { currentHp = Math.max(0, currentHp - dmg); }

    public boolean applyStatus(StatusCondition condition) {
        if(status != StatusCondition.NONE) {
            return false;
        }
        this.status = condition;

        if (status == StatusCondition.TOX) { statusCounter = 1; }

        return true;
    }

    public int applyEndOfTurnDamage(){
        // Status zadający ma min 1 obrażenia (przy niskim maxHp dzielenie dałoby 0).
        int damage = switch (status) {
            case PSN -> Math.max(1, getMaxHp() / 8);
            case BRN -> Math.max(1, getMaxHp() / 16);
            case TOX -> {
                int d = Math.max(1, getMaxHp() * statusCounter / 16);
                statusCounter++;   // eskalacja: następny tick mocniejszy
                yield d;
            }
            default -> 0;   // NONE, PAR, SLP, FRZ — brak ticku obrażeń
        };

        takeDamage(damage);
        return damage;
    }

    public Move moveAt(int index) {
        if(index < 0 || index >= moves.size()) {
            throw new IllegalArgumentException("moveIndex poza zakresem: " + index);
        }
        return moves.get(index).getMove();
    }

    public Move useMove(int index) {
        MoveSlot slot = moves.get(index);
        slot.use();
        return slot.getMove();
    }


}
