package dev.adamgrochulski.javamon.engine;

// Podział ruchu. PHYSICAL liczy z atk/def, SPECIAL ze spAtk/spDef,
// STATUS nie zadaje obrażeń (sam efekt: status, staty, hazardy).
public enum MoveCategory {
    PHYSICAL, SPECIAL, STATUS
}
