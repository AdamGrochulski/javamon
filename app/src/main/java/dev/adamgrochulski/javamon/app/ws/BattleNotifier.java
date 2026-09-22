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

/** Rozsyłanie ramek walki. Każdy gracz dostaje własną wersję: eventy filtrowane, seq własne. */
@Component
public class BattleNotifier {

    private final WsSessionRegistry registry;
    private final BattleSessionService sessions;
    private final Duration actionTimeout;

    BattleNotifier(WsSessionRegistry registry, BattleSessionService sessions, BattleProperties properties) {
        this.registry = registry;
        this.sessions = sessions;
        this.actionTimeout = properties.actionTimeout();
    }

    public void start(BattleSession session) {
        for (Player player : Player.values()) {
            send(session, player, "BATTLE_START", battleStart(session, player));
        }
        requestNext(session);
    }

    public void afterAction(BattleSession session, int turn, List<BattleEvent> events) {
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
    }

    private void requestNext(BattleSession session) {
        Battle battle = session.battle();
        List<Player> awaiting = battle.awaitingReplacement();
        boolean replacement = !awaiting.isEmpty();
        List<Player> asked = replacement ? awaiting : List.of(Player.P1, Player.P2);

        // Deadline jest informacyjny: timer i akcja wybierana przez serwer to osobny plaster.
        Instant deadline = Instant.now().plus(actionTimeout);
        for (Player player : asked) {
            send(session, player, "REQUEST_ACTION", new WsDtos.RequestAction(
                    session.id(), battle.getTurn(), replacement ? "REPLACEMENT" : "TURN",
                    deadline, sessions.legalActions(session, player)));
        }
    }

    // seq rośnie też dla rozłączonego: po RESUME ma być widać, że coś go ominęło.
    private void send(BattleSession session, Player player, String type, Object payload) {
        long seq = session.nextSeq(player);
        registry.find(session.participant(player).trainerId())
                .ifPresent(connection -> connection.send(type, seq, payload));
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
