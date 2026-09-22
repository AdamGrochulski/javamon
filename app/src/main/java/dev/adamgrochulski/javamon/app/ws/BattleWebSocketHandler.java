package dev.adamgrochulski.javamon.app.ws;

import dev.adamgrochulski.javamon.app.auth.AuthenticatedTrainer;
import dev.adamgrochulski.javamon.app.auth.JwtService;
import dev.adamgrochulski.javamon.app.battle.BattleSession;
import dev.adamgrochulski.javamon.app.battle.BattleSessionService;
import dev.adamgrochulski.javamon.engine.battle.Action;
import dev.adamgrochulski.javamon.engine.battle.ForfeitAction;
import dev.adamgrochulski.javamon.engine.battle.MoveAction;
import dev.adamgrochulski.javamon.engine.battle.SwitchAction;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Warstwa transportowa protokołu: koperta, uwierzytelnienie, cykl życia
 * połączenia. Logika walki tu nie wchodzi — dojdzie jako osobny serwis
 * (Krok 7), do którego ten handler będzie tylko kierował ramki.
 */
@Component
public class BattleWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BattleWebSocketHandler.class);

    private final WsJson json;
    private final WsSessionRegistry registry;
    private final JwtService jwtService;
    private final TaskScheduler scheduler;
    private final WsProperties properties;
    private final BattleSessionService sessions;
    private final BattleNotifier notifier;

    BattleWebSocketHandler(WsJson json, WsSessionRegistry registry, JwtService jwtService,
                           TaskScheduler scheduler, WsProperties properties,
                           BattleSessionService sessions, BattleNotifier notifier) {
        this.json = json;
        this.registry = registry;
        this.jwtService = jwtService;
        this.scheduler = scheduler;
        this.properties = properties;
        this.sessions = sessions;
        this.notifier = notifier;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WsConnection connection = new WsConnection(session, json);
        registry.open(connection);
        // Połączenie istnieje, zanim wiemy, kto to. Timeout ogranicza to okno,
        // a do AUTH_OK i tak nie wolno wysłać niczego innego.
        connection.scheduleAuthTimeout(scheduler, properties.authTimeout());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WsConnection connection = registry.bySessionId(session.getId()).orElse(null);
        if (connection == null) {
            return;     // ramka po zamknięciu — nie ma komu odpowiedzieć
        }
        try {
            dispatch(connection, json.read(message.getPayload()));
        } catch (WsException ex) {
            connection.sendError(ex.code(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            // Rekordy akcji odsiewają bzdury same (ujemny indeks). To wina klienta, nie awaria.
            connection.sendError(WsErrorCode.BAD_FRAME, "Payload ma niedozwolone wartości");
        } catch (Exception ex) {
            // Ta sama zasada co w ApiErrorHandler: klient dostaje stały
            // komunikat, szczegóły idą wyłącznie do logu.
            log.error("Nieobsłużony błąd ramki WS", ex);
            connection.sendError(WsErrorCode.INTERNAL_ERROR, "Coś poszło nie tak");
        }
    }

    private void dispatch(WsConnection connection, ClientFrame frame) {
        if (!connection.isAuthenticated() && !"AUTH".equals(frame.type())) {
            throw new WsException(WsErrorCode.UNAUTHENTICATED, "Najpierw ramka AUTH");
        }

        switch (frame.type()) {
            case "AUTH" -> authenticate(connection, json.payload(frame, WsDtos.AuthRequest.class));
            case "PING" -> connection.send("PONG", null);
            case "QUEUE_JOIN", "QUEUE_LEAVE" ->
                    throw new WsException(WsErrorCode.NOT_IMPLEMENTED, "Matchmaking dojdzie później");
            case "MOVE" -> {
                WsDtos.MoveRequest request = json.payload(frame, WsDtos.MoveRequest.class);
                submit(connection, request.battleId(), request.turn(), new MoveAction(request.moveIndex()));
            }
            case "SWITCH" -> {
                WsDtos.SwitchRequest request = json.payload(frame, WsDtos.SwitchRequest.class);
                submit(connection, request.battleId(), request.turn(), new SwitchAction(request.benchIndex()));
            }
            case "FORFEIT" -> {
                WsDtos.ForfeitRequest request = json.payload(frame, WsDtos.ForfeitRequest.class);
                submit(connection, request.battleId(), null, new ForfeitAction());
            }
            case "RESUME" ->
                    throw new WsException(WsErrorCode.NOT_IMPLEMENTED, "Wznowienie dojdzie później");
            default ->
                    throw new WsException(WsErrorCode.BAD_FRAME, "Nieznany typ ramki: " + frame.type());
        }
    }

    private void submit(WsConnection connection, UUID battleId, Integer turn, Action action) {
        UUID trainerId = connection.trainer().id();
        BattleSession session = sessions.require(battleId, trainerId);
        // FORFEIT nie niesie numeru tury: poddać się można w każdej fazie.
        int onTurn = turn != null ? turn : session.battle().getTurn();

        sessions.submit(battleId, trainerId, onTurn, action)
                .ifPresent(events -> notifier.afterAction(session, onTurn, events));
    }

    private void authenticate(WsConnection connection, WsDtos.AuthRequest request) {
        if (connection.isAuthenticated()) {
            throw new WsException(WsErrorCode.BAD_FRAME, "Sesja jest już uwierzytelniona");
        }

        AuthenticatedTrainer trainer = jwtService.verify(request.token()).orElse(null);
        if (trainer == null) {
            // Jedyny przypadek, w którym zamykamy zamiast odpowiedzieć ERROR:
            // problem dotyczy tożsamości, a nie pojedynczej ramki.
            connection.close(WsClose.UNAUTHORIZED, "Token odrzucony");
            return;
        }

        connection.authenticate(trainer);
        registry.bind(trainer.id(), connection).ifPresent(previous ->
                previous.close(WsClose.SESSION_REPLACED, "To konto połączyło się gdzie indziej"));

        connection.send("AUTH_OK",
                new WsDtos.AuthOk(trainer.id(), trainer.username(), trainer.guest()));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        // TextWebSocketHandler domyślnie zamyka tu sesję kodem 1003. Protokół
        // mówi co innego: zła ramka to błąd, nie powód do zrywania połączenia.
        registry.bySessionId(session.getId()).ifPresent(connection ->
                connection.sendError(WsErrorCode.BAD_FRAME, "Protokół jest tekstowy"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.bySessionId(session.getId()).ifPresent(connection -> {
            connection.cancelAuthTimeout();
            registry.close(connection);
        });
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Błąd transportu sesji {}", session.getId(), exception);
    }
}
