package dev.adamgrochulski.javamon.engine.model;

/**
 * Pogoda na polu walki. Wpływa na obrażenia (RAIN/SUN modyfikują Water/Fire)
 * i chip end-of-turn (SANDSTORM rani niekryte typy). Trwa kilka tur, potem NONE.
 */
public enum Weather {
    NONE, RAIN, SUN, SANDSTORM, SNOW
}
