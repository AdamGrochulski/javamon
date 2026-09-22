package dev.adamgrochulski.javamon.app.ws;

import dev.adamgrochulski.javamon.app.auth.AuthenticatedTrainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * Jedno połączenie WebSocket wraz z tożsamością, jeśli już się przedstawiło.
 */
public final class WsConnection {

    private static final Logger log = LoggerFactory.getLogger(WsConnection.class);

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT = 512 * 1024;

    private final WebSocketSession session;
    private final WsJson json;

    private volatile AuthenticatedTrainer trainer;
    private volatile ScheduledFuture<?> authTimeout;

    WsConnection(WebSocketSession raw, WsJson json) {
        this.session = new ConcurrentWebSocketSessionDecorator(
                raw, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT);
        this.json = json;
    }

    public String id() {
        return session.getId();
    }

    public boolean isAuthenticated() {
        return trainer != null;
    }

    public AuthenticatedTrainer trainer() {
        return trainer;
    }

    void authenticate(AuthenticatedTrainer trainer) {
        this.trainer = trainer;
        cancelAuthTimeout();
    }

    void scheduleAuthTimeout(TaskScheduler scheduler, Duration timeout) {
        authTimeout = scheduler.schedule(() -> {
            if (!isAuthenticated()) {
                close(WsClose.AUTH_TIMEOUT, "Brak ramki AUTH");
            }
        }, Instant.now().plus(timeout));
    }

    void cancelAuthTimeout() {
        ScheduledFuture<?> pending = authTimeout;
        if (pending != null) {
            pending.cancel(false);
        }
    }

    public void send(String type, Object payload) {
        send(new ServerFrame(type, null, payload));
    }

    public void send(String type, long seq, Object payload) {
        send(new ServerFrame(type, seq, payload));
    }

    public void sendError(WsErrorCode code, String message) {
        send("ERROR", new WsDtos.ErrorPayload(code, message));
    }

    /** Ramka z gotowym numerem porządkowym - nadaje go BattleFrameLog. */
    public void send(ServerFrame frame) {
        try {
            session.sendMessage(new TextMessage(json.write(frame)));
        } catch (IOException | IllegalStateException ex) {
            log.debug("Nie udało się wysłać ramki {} do sesji {}", frame.type(), id(), ex);
        }
    }

    public void close(int code, String reason) {
        try {
            session.close(new CloseStatus(code, reason));
        } catch (IOException ex) {
            log.debug("Zamknięcie sesji {} nie powiodło się", id(), ex);
        }
    }
}
