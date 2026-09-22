package dev.adamgrochulski.javamon.app.ws;

import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import dev.adamgrochulski.javamon.app.battle.LegalActions;
import dev.adamgrochulski.javamon.engine.battle.Player;
import dev.adamgrochulski.javamon.engine.model.StatusCondition;
import dev.adamgrochulski.javamon.engine.model.Type;

import java.time.Instant;
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

    public record MoveRequest(UUID battleId, int turn, int moveIndex) {
    }

    public record SwitchRequest(UUID battleId, int turn, int benchIndex) {
    }

    public record ForfeitRequest(UUID battleId) {
    }

    public record QueueJoinRequest(UUID teamId) {
    }

    public record ResumeRequest(UUID battleId, long lastSeq) {
    }

    public record Queued(Instant since) {
    }

    public record BattleStart(UUID battleId, Player you, Opponent opponent,
                              List<TeamMember> yourTeam, OpponentActive opponentActive) {
    }

    public record Opponent(String username, int rating) {
    }

    public record TeamMember(int teamIndex, String speciesId, String name, int level,
                             int maxHp, int currentHp, StatusCondition status,
                             List<Type> types, List<MoveView> moves) {
    }

    public record MoveView(int index, String name, Type type, int pp, int maxPp) {
    }

    /** Bez maxHp i bez movesetu - to jest informacja ukryta. */
    public record OpponentActive(int teamIndex, String name, int level, int hpPercent,
                                 StatusCondition status, List<Type> types) {
    }

    public record RequestAction(UUID battleId, int turn, String kind, Instant deadline, LegalActions legal) {
    }

    /** ratingBefore i ratingAfter zostają null, dopóki nie ma ELO. */
    public record BattleEndPayload(UUID battleId, Player winner, String reason,
                                   Integer ratingBefore, Integer ratingAfter) {
    }
}
