package dev.adamgrochulski.javamon.engine.model;

/**
 * Teren na polu walki (dotyczy tylko naziemnych). Wzmacnia pasujący typ ruchu
 * (Electric/Grassy/Psychic ×1.3), MISTY tłumi Dragon ×0.5 na naziemny cel,
 * GRASSY leczy naziemnych 1/16 co turę. Trwa kilka tur, potem NONE.
 */
public enum Terrain {
    NONE, ELECTRIC, GRASSY, MISTY, PSYCHIC
}
