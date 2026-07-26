package dev.adamgrochulski.javamon.app.ws;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;

/**
 * Nakładka adnotacji na {@link BattleEvent}. Silnik nie wie o istnieniu JSON-a,
 * a nazwy wariantów na drucie należą do protokołu, nie do nazw klas w silniku:
 * zmiana nazwy rekordu nie ma prawa zepsuć frontu.
 * <p>
 * Tabela musi zgadzać się z sekcją "Mapowanie BattleEvent → JSON"
 * w docs/protocol.md. Pilnuje tego test BattleEventJsonTest.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BattleEvent.Switch.class, name = "SWITCH"),
        @JsonSubTypes.Type(value = BattleEvent.MoveUsed.class, name = "MOVE_USED"),
        @JsonSubTypes.Type(value = BattleEvent.MoveMissed.class, name = "MOVE_MISSED"),
        @JsonSubTypes.Type(value = BattleEvent.Damage.class, name = "DAMAGE"),
        @JsonSubTypes.Type(value = BattleEvent.NoEffect.class, name = "NO_EFFECT"),
        @JsonSubTypes.Type(value = BattleEvent.Faint.class, name = "FAINT"),
        @JsonSubTypes.Type(value = BattleEvent.StatusTick.class, name = "STATUS_TICK"),
        @JsonSubTypes.Type(value = BattleEvent.StatusInflicted.class, name = "STATUS_INFLICTED"),
        @JsonSubTypes.Type(value = BattleEvent.StatStageChanged.class, name = "STAT_STAGE_CHANGED"),
        @JsonSubTypes.Type(value = BattleEvent.Healed.class, name = "HEALED"),
        @JsonSubTypes.Type(value = BattleEvent.RecoilDamage.class, name = "RECOIL_DAMAGE"),
        @JsonSubTypes.Type(value = BattleEvent.HazardSet.class, name = "HAZARD_SET"),
        @JsonSubTypes.Type(value = BattleEvent.HazardHurt.class, name = "HAZARD_HURT"),
        @JsonSubTypes.Type(value = BattleEvent.Immobilized.class, name = "IMMOBILIZED"),
        @JsonSubTypes.Type(value = BattleEvent.Flinched.class, name = "FLINCHED"),
        @JsonSubTypes.Type(value = BattleEvent.ConfusionStarted.class, name = "CONFUSION_STARTED"),
        @JsonSubTypes.Type(value = BattleEvent.ConfusionHit.class, name = "CONFUSION_HIT"),
        @JsonSubTypes.Type(value = BattleEvent.ConfusionEnded.class, name = "CONFUSION_ENDED"),
        @JsonSubTypes.Type(value = BattleEvent.Charging.class, name = "CHARGING"),
        @JsonSubTypes.Type(value = BattleEvent.Recharging.class, name = "RECHARGING"),
        @JsonSubTypes.Type(value = BattleEvent.Trapped.class, name = "TRAPPED"),
        @JsonSubTypes.Type(value = BattleEvent.TrapHurt.class, name = "TRAP_HURT"),
        @JsonSubTypes.Type(value = BattleEvent.TrapEnded.class, name = "TRAP_ENDED"),
        @JsonSubTypes.Type(value = BattleEvent.ProtectStarted.class, name = "PROTECT_STARTED"),
        @JsonSubTypes.Type(value = BattleEvent.Protected.class, name = "PROTECTED"),
        @JsonSubTypes.Type(value = BattleEvent.MoveFailed.class, name = "MOVE_FAILED"),
        @JsonSubTypes.Type(value = BattleEvent.OneHitKO.class, name = "ONE_HIT_KO"),
        @JsonSubTypes.Type(value = BattleEvent.Seeded.class, name = "SEEDED"),
        @JsonSubTypes.Type(value = BattleEvent.LeechSeedDrain.class, name = "LEECH_SEED_DRAIN"),
        @JsonSubTypes.Type(value = BattleEvent.WeatherStarted.class, name = "WEATHER_STARTED"),
        @JsonSubTypes.Type(value = BattleEvent.WeatherHurt.class, name = "WEATHER_HURT"),
        @JsonSubTypes.Type(value = BattleEvent.WeatherEnded.class, name = "WEATHER_ENDED"),
        @JsonSubTypes.Type(value = BattleEvent.ScreenSet.class, name = "SCREEN_SET"),
        @JsonSubTypes.Type(value = BattleEvent.ScreenFaded.class, name = "SCREEN_FADED"),
        @JsonSubTypes.Type(value = BattleEvent.TerrainStarted.class, name = "TERRAIN_STARTED"),
        @JsonSubTypes.Type(value = BattleEvent.TerrainEnded.class, name = "TERRAIN_ENDED"),
        @JsonSubTypes.Type(value = BattleEvent.Forfeit.class, name = "FORFEIT"),
        @JsonSubTypes.Type(value = BattleEvent.BattleEnd.class, name = "BATTLE_END")
})
public abstract class BattleEventMixin {
}