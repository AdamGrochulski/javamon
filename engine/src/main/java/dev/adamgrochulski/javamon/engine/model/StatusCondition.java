package dev.adamgrochulski.javamon.engine.model;

/**
 * Status niezmienny (non-volatile) — max jeden naraz. TOX eskaluje (licznik).
 * <p>
 * MVP: zaimplementowany tylko tick końca tury (BRN/PSN/TOX zadają obrażenia).
 * Do domknięcia z pełną integracją w resolverze:
 * blokada ruchu (SLP/FRZ/PAR) i modyfikacja statów (BRN -atak, PAR -speed).
 */
public enum StatusCondition {
    NONE, BRN, PSN, TOX, PAR, SLP, FRZ
}
