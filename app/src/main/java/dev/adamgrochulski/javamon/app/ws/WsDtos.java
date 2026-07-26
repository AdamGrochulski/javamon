package dev.adamgrochulski.javamon.app.ws;

import dev.adamgrochulski.javamon.engine.battle.BattleEvent;

import java.util.List;
import java.util.UUID;

public final class WsDtos {

    private WsDtos() {
    }

    public record AuthRequest(String token) {
    }

    public record AuthOk(UUID trainerId, String username, boolean guest) {
    }

    public record ErrorPayload(WsErrorCode code, String message) {
    }

    /**
     * Lista musi być zadeklarowana jako {@code List<BattleEvent>}, nie Object
     * ani List&lt;?&gt;. Jackson dokleja dyskryminator "type" na podstawie typu
     * deklarowanego — przy Objectcie po prostu by go nie było, a mixin nigdy
     * by się nie odezwał.
     */
    public record TurnEvents(UUID battleId, int turn, List<BattleEvent> events) {
    }
}
