package dev.adamgrochulski.javamon.engine.model;

/**
 * Staty bojowe podlegające boostom/debuffom w trakcie walki (stage -6..+6).
 * HP celowo poza — punktów życia nie boostuje się stopniami.
 */
public enum Stat {
    ATTACK, DEFENSE, SPECIAL_ATTACK, SPECIAL_DEFENSE, SPEED
}
