package dev.adamgrochulski.javamon.engine.model;

/**
 * Efekt utrzymujący się po stronie gracza (nie na pojedynczym Pokémonie).
 * Hazardy wejściowe: STEALTH_ROCK (rani zależnie od typu ROCK), SPIKES
 * (warstwowe, tylko naziemne), TOXIC_SPIKES (zatruwa naziemne, warstwowe),
 * STICKY_WEB (obniża Speed naziemnym).
 * <p>
 * {@code maxLayers} = ile razy hazard można nałożyć (Spikes do 3, Toxic Spikes do 2).
 * Ekrany (REFLECT/LIGHT_SCREEN/AURORA_VEIL) mają 1 warstwę, ale własne trwanie
 * (liczone osobno w BattleSide) i redukują obrażenia zamiast ranić.
 */
public enum SideCondition {
    STEALTH_ROCK(1),
    SPIKES(3),
    TOXIC_SPIKES(2),
    STICKY_WEB(1),
    REFLECT(1),
    LIGHT_SCREEN(1),
    AURORA_VEIL(1);

    private final int maxLayers;

    SideCondition(int maxLayers) {
        this.maxLayers = maxLayers;
    }

    public int maxLayers() {
        return maxLayers;
    }
}
