package dev.adamgrochulski.javamon.app.ws;

import dev.adamgrochulski.javamon.app.battle.*;
import dev.adamgrochulski.javamon.engine.battle.Battle;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import dev.adamgrochulski.javamon.engine.battle.BattleSide;
import dev.adamgrochulski.javamon.engine.battle.Player;
import dev.adamgrochulski.javamon.engine.model.BattlePokemon;
import dev.adamgrochulski.javamon.engine.model.Move;
import dev.adamgrochulski.javamon.engine.model.Type;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Rozsyłanie ramek walki. Każdy gracz dostaje własną wersję: eventy filtrowane, seq własne. */
@Component
public class BattleNotifier {

    private final WsSessionRegistry registry;
    private final BattleSessionService sessions;
    private final BattleFrameLog frames;
    private final BattleTimers timers;
    private final Duration actionTimeout;

    BattleNotifier(WsSessionRegistry registry, BattleSessionService sessions, BattleFrameLog frames,
                   BattleTimers timers, BattleProperties properties) {
        this.registry = registry;
        this.sessions = sessions;
        this.frames = frames;
        this.timers = timers;
        this.actionTimeout = properties.actionTimeout();
    }

    public void start(BattleSession session) {
        for (Player player : Player.values()) {
            send(session, player, "BATTLE_START", battleStart(session, player));
        }
        requestNext(session);
    }

    public void afterAction(BattleSession session, int turn, List<BattleEvent> events) {
        timers.cancel(session.id());
        for (Player player : Player.values()) {
            send(session, player, "TURN_EVENTS", new WsDtos.TurnEvents(
                    session.id(), turn, BattleEventFilter.forPlayer(session.battle(), player, events)));
        }

        // Po evencie, nie po isOver(): poddanie nie kładzie żadnej strony.
        BattleEvent.BattleEnd end = endOf(events);
        if (end == null) {
            requestNext(session);
            return;
        }
        String reason = events.stream().anyMatch(e -> e instanceof BattleEvent.Forfeit) ? "forfeit" : "faint";
        for (Player player : Player.values()) {
            send(session, player, "BATTLE_END",
                    new WsDtos.BattleEndPayload(session.id(), end.winner(), reason, null, null));
        }
        frames.forget(session.id());
    }

    /**
     * Dosyłka po zerwanym połączeniu. Gdy bufor nie sięga tak daleko, idzie pełny stan
     * i ostatnie pytanie o akcję - z oryginalnym deadlinem, bo zegar tury nie stoi.
     */
    public void resume(BattleSession session, Player player, long lastSeq) {
        WsConnection connection = registry.find(session.participant(player).trainerId()).orElse(null);
        if (connection == null) {
            return;
        }

        Optional<List<ServerFrame>> missed = frames.since(session.id(), player, lastSeq);
        if (missed.isPresent()) {
            missed.get().forEach(connection::send);
            return;
        }

        connection.send(frames.record(session.id(), player, "BATTLE_START", battleStart(session, player)));
        frames.last(session.id(), player, "REQUEST_ACTION").ifPresent(frame ->
                connection.send(frames.record(session.id(), player, "REQUEST_ACTION", frame.payload())));
    }

    private void requestNext(BattleSession session) {
        Battle battle = session.battle();
        List<Player> awaiting = battle.awaitingReplacement();
        boolean replacement = !awaiting.isEmpty();
        List<Player> asked = replacement ? awaiting : List.of(Player.P1, Player.P2);

        int turn = battle.getTurn();
        Instant deadline = Instant.now().plus(actionTimeout);
        for (Player player : asked) {
            send(session, player, "REQUEST_ACTION", new WsDtos.RequestAction(
                    session.id(), turn, replacement ? "REPLACEMENT" : "TURN",
                    deadline, sessions.legalActions(session, player)));
        }
        timers.arm(session.id(), deadline, () -> expire(session, turn));
    }

    // Timer bywa spóźniony o ułamek sekundy - serwis sam odrzuci rozliczenie, które się już odbyło.
    private void expire(BattleSession session, int turn) {
        sessions.timeout(session.id(), turn).ifPresent(events -> afterAction(session, turn, events));
    }

    // Ramka trafia do logu także dla rozłączonego: po RESUME ma być co dosłać.
    private void send(BattleSession session, Player player, String type, Object payload) {
        ServerFrame frame = frames.record(session.id(), player, type, payload);
        registry.find(session.participant(player).trainerId())
                .ifPresent(connection -> connection.send(frame));
    }

    private static BattleEvent.BattleEnd endOf(List<BattleEvent> events) {
        return events.stream()
                .filter(BattleEvent.BattleEnd.class::isInstance)
                .map(BattleEvent.BattleEnd.class::cast)
                .findFirst()
                .orElse(null);
    }

    private WsDtos.BattleStart battleStart(BattleSession session, Player player) {
        BattleSide mine = session.battle().side(player);
        Participant opponent = session.participant(player.opponent());

        List<WsDtos.TeamMember> team = new ArrayList<>();
        for (int i = 0; i < mine.getTeam().size(); i++) {
            team.add(teamMember(session, player, i, mine.getTeam().get(i)));
        }

        return new WsDtos.BattleStart(session.id(), player,
                new WsDtos.Opponent(opponent.username(), opponent.rating()),
                team, opponentActive(session, player.opponent()));
    }

    private WsDtos.TeamMember teamMember(BattleSession session, Player player, int index, BattlePokemon mon) {
        List<WsDtos.MoveView> moves = new ArrayList<>();
        for (int i = 0; i < mon.moveCount(); i++) {
            Move move = mon.moveAt(i);
            moves.add(new WsDtos.MoveView(i, move.name(), move.type(), mon.ppLeft(i), move.pp()));
        }

        return new WsDtos.TeamMember(index, session.speciesId(player, index), mon.getName(), mon.getLevel(),
                mon.getMaxHp(), mon.getCurrentHp(), mon.getStatus(), typesOf(mon), moves);
    }

    private WsDtos.OpponentActive opponentActive(BattleSession session, Player side) {
        BattleSide opponentSide = session.battle().side(side);
        BattlePokemon active = opponentSide.active();

        return new WsDtos.OpponentActive(opponentSide.getActiveIndex(), active.getName(), active.getLevel(),
                BattleEventFilter.percentOf(active.getCurrentHp(), active.getMaxHp()),
                active.getStatus(), typesOf(active));
    }

    private static List<Type> typesOf(BattlePokemon mon) {
        return mon.getSecondary() == null
                ? List.of(mon.getPrimary())
                : List.of(mon.getPrimary(), mon.getSecondary());
    }
}
