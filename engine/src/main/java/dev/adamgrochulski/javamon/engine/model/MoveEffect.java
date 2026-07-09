package dev.adamgrochulski.javamon.engine.model;

/**
 * Efekt uboczny ruchu — odpalany po trafieniu. Przy ruchu STATUS to jedyne działanie;
 * przy ruchu damage'owym efekty lecą PO zadaniu obrażeń (secondary effect).
 * <p>
 * Sealed: resolver obsługuje każdy wariant przez exhaustive switch, więc nowy typ
 * efektu nie przejdzie niezauważony. Kolejne warianty (StatChange, Hazard, ForceSwitch...)
 * dochodzą w następnych krokach Fazy 1.5.
 */
public sealed interface MoveEffect permits MoveEffect.InflictStatus {

    /** Kogo dotyczy efekt względem używającego ruchu. */
    enum Target { SELF, OPPONENT }

    /** Szansa 1..100 na zadziałanie. 100 = zawsze (resolver pomija rzut RNG). */
    int chance();

    /** Cel efektu. */
    Target target();

    /** Nakłada status na cel (Toxic 100% na przeciwnika, Flamethrower 10% burn itd.). */
    record InflictStatus(StatusCondition status, Target target, int chance) implements MoveEffect {
        public InflictStatus {
            if (status == null || status == StatusCondition.NONE) {
                throw new IllegalArgumentException("InflictStatus wymaga realnego statusu, było: " + status);
            }
            if (chance < 1 || chance > 100) {
                throw new IllegalArgumentException("chance musi być w 1..100, było: " + chance);
            }
        }
    }
}
