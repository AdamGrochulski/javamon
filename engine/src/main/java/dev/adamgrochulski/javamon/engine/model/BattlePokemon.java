package dev.adamgrochulski.javamon.engine.model;

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

    // Volatile na czas jednej tury — kasowany na końcu tury (clearTurnVolatiles).
    private boolean flinched;

    // Zmieszanie (confusion): licznik tur > 0 = zmieszany. Trwa między turami,
    // znika po odliczeniu (nie kasowane przez clearTurnVolatiles).
    private int confusionTurns;

    // Stan ruchów dwuturowych: chargingMove != null = ładuje (wypuści w następnej
    // turze); mustRecharge = odpoczywa po ruchu typu RECHARGE (Hyper Beam).
    private Move chargingMove;
    private boolean mustRecharge;

    // Stopnie statów bojowych (-6..+6), start 0. Indeksowane przez Stat.ordinal().
    private final int[] stages = new int[Stat.values().length];

    // Staty przeliczone na level (liczone raz w konstruktorze).
    private final BattleStats derived;
    private final List<MoveSlot> moves;

    // Jedyny stan zmienny na tym etapie — spada od obrażeń.
    private int currentHp;

    public BattlePokemon(String name, Stats base, Type primary, Type secondary, int level, List<Move> moves) {
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

        if (moves == null) {
            throw new IllegalArgumentException("moveset jest wymagany");
        }

        if (moves.isEmpty()) {
            throw new IllegalArgumentException("moveset musi mieć co najmniej 1 ruch");
        }

        if (moves.size() > 4) {
            throw new IllegalArgumentException("moveset: max 4 ruchy, było: " + moves.size());
        }

        if (moves.stream()
                .anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("moveset nie może zawierać null");
        }

        List<MoveSlot> slots = moves.stream()
                .map(MoveSlot::new).toList();

        this.moves = List.copyOf(slots);
    }

    public String getName() {
        return name;
    }

    public Type getPrimary() {
        return primary;
    }

    public Type getSecondary() {
        return secondary;
    }

    public int getLevel() {
        return level;
    }

    public int getMaxHp() {
        return derived.maxHp();
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public int getAttack() {
        return derived.attack();
    }

    public int getDefense() {
        return derived.defense();
    }

    public int getSpecialAttack() {
        return derived.specialAttack();
    }

    public int getSpecialDefense() {
        return derived.specialDefense();
    }

    public int getSpeed() {
        return derived.speed();
    }

    public StatusCondition getStatus() {
        return status;
    }

    public int getStatusCounter() {
        return statusCounter;
    }

    public boolean isFainted() {
        return currentHp <= 0;
    }

    // Obcięcie do 0 — HP nie schodzi poniżej zera.
    public void takeDamage(int dmg) {
        currentHp = Math.max(0, currentHp - dmg);
    }

    /** Leczy o amount, nie przekraczając maxHp; nie ożywia trupa. Zwraca faktycznie uleczoną ilość. */
    public int heal(int amount) {
        if (amount <= 0 || isFainted()) {
            return 0;
        }
        int before = currentHp;
        currentHp = Math.min(getMaxHp(), currentHp + amount);
        return currentHp - before;
    }

    public boolean applyStatus(StatusCondition condition) {
        if (status != StatusCondition.NONE) {
            return false;
        }
        this.status = condition;

        if (status == StatusCondition.TOX) {
            statusCounter = 1;
        }

        return true;
    }

    /**
     * Usypia na podaną liczbę tur. Długość snu rolluje wołający (RNG) —
     * model tylko trzyma licznik, żeby zachować determinizm silnika.
     * Nie nadpisuje istniejącego statusu (jak applyStatus).
     */
    public boolean applySleep(int turns) {
        if (turns < 1) {
            throw new IllegalArgumentException("tura snu musi być >= 1, było: " + turns);
        }
        if (status != StatusCondition.NONE) {
            return false;
        }
        status = StatusCondition.SLP;
        statusCounter = turns;
        return true;
    }

    /**
     * Konsumuje jedną turę snu. Zwraca true, jeśli mon spał (ruch zablokowany) —
     * włącznie z turą budzącą, więc traci dokładnie tyle tur, ile dostał w applySleep.
     * Gdy licznik zejdzie do 0, budzi się (status wraca do NONE).
     */
    public boolean sleepTurn() {
        if (status != StatusCondition.SLP) {
            return false;
        }
        statusCounter--;
        if (statusCounter <= 0) {
            status = StatusCondition.NONE;
            statusCounter = 0;
        }
        return true;
    }

    /** Rozmraża (FRZ -> NONE). No-op, jeśli nie zamrożony. Rzut szansy robi wołający (RNG). */
    public void thaw() {
        if (status == StatusCondition.FRZ) {
            status = StatusCondition.NONE;
        }
    }

    /** Ustawia flinch (wzdrygnięcie) na tę turę. */
    public void flinch() { this.flinched = true; }

    public boolean isFlinched() { return flinched; }

    /** Kasuje volatile'e żyjące tylko jedną turę (flinch). Woła resolver na końcu tury. */
    public void clearTurnVolatiles() { this.flinched = false; }

    /** Nakłada zmieszanie na {@code turns} tur (RNG rolluje wołający). No-op, jeśli już zmieszany. */
    public void confuse(int turns) {
        if (turns < 1) {
            throw new IllegalArgumentException("tury zmieszania muszą być >= 1, było: " + turns);
        }
        if (confusionTurns == 0) {
            confusionTurns = turns;
        }
    }

    public boolean isConfused() { return confusionTurns > 0; }

    /** Rozpoczyna ładowanie ruchu dwuturowego (wypuści w następnej turze). */
    public void startCharge(Move move) { this.chargingMove = move; }

    public boolean isCharging() { return chargingMove != null; }

    /** Wypuszcza naładowany ruch i kasuje stan ładowania. */
    public Move releaseCharge() {
        Move move = chargingMove;
        chargingMove = null;
        return move;
    }

    public boolean mustRecharge() { return mustRecharge; }

    public void setMustRecharge() { this.mustRecharge = true; }

    public void clearRecharge() { this.mustRecharge = false; }

    /**
     * Odlicza turę zmieszania (wołane przy próbie ruchu). Zwraca true, jeśli po
     * odliczeniu mon nadal zmieszany (może uderzyć siebie); false = właśnie oprzytomniał.
     */
    public boolean tickConfusion() {
        if (confusionTurns > 0) {
            confusionTurns--;
        }
        return confusionTurns > 0;
    }

    /** Maksymalny (i minimalny, ze znakiem) stopień statu. */
    public static final int MAX_STAGE = 6;

    public int getStage(Stat stat) {
        return stages[stat.ordinal()];
    }

    /**
     * Zmienia stopień statu o delta, obcinając do [-6, +6].
     * Zwraca faktycznie zastosowaną zmianę (0 = stat był już przy limicie).
     */
    public int changeStage(Stat stat, int delta) {
        int old = stages[stat.ordinal()];
        int next = Math.max(-MAX_STAGE, Math.min(MAX_STAGE, old + delta));
        stages[stat.ordinal()] = next;
        return next - old;
    }

    // Stat po nałożeniu stopnia. Ułamek na intach (jak w grach): +n -> *(2+n)/2,
    // -n -> *2/(2+n). Dzielenie całkowite = floor, bez błędów floating-point.
    private int effective(int raw, Stat stat) {
        int stage = stages[stat.ordinal()];
        int numerator = stage >= 0 ? 2 + stage : 2;
        int denominator = stage >= 0 ? 2 : 2 - stage;
        return raw * numerator / denominator;
    }

    public int getEffectiveAttack() { return effective(getAttack(), Stat.ATTACK); }

    public int getEffectiveDefense() { return effective(getDefense(), Stat.DEFENSE); }

    public int getEffectiveSpecialAttack() { return effective(getSpecialAttack(), Stat.SPECIAL_ATTACK); }

    public int getEffectiveSpecialDefense() { return effective(getSpecialDefense(), Stat.SPECIAL_DEFENSE); }

    public int getEffectiveSpeed() { return effective(getSpeed(), Stat.SPEED); }

    public int applyEndOfTurnDamage() {
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
        if (index < 0 || index >= moves.size()) {
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
