package dev.adamgrochulski.javamon.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prawdziwy klient WebSocket na potrzeby testów. MockMvc tu nie wystarcza —
 * upgrade połączenia dzieje się w kontenerze, poniżej warstwy, którą MockMvc
 * potrafi udawać.
 */
class WsTestClient extends TextWebSocketHandler implements AutoCloseable {

    private static final long TIMEOUT_MS = 5_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();

    private WebSocketSession session;

    static WsTestClient connect(int port) throws Exception {
        WsTestClient client = new WsTestClient();
        client.session = new StandardWebSocketClient()
                .execute(client, new WebSocketHttpHeaders(),
                        URI.create("ws://localhost:" + port + "/ws/battle"))
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return client;
    }

    void send(String type, String payloadJson) throws Exception {
        String frame = payloadJson == null
                ? "{\"type\":\"%s\"}".formatted(type)
                : "{\"type\":\"%s\",\"payload\":%s}".formatted(type, payloadJson);
        sendRaw(frame);
    }

    void sendRaw(String raw) throws Exception {
        session.sendMessage(new TextMessage(raw));
    }

    /** Kolejna ramka albo błąd testu — cisza tam, gdzie oczekujemy odpowiedzi, to też porażka. */
    JsonNode nextFrame() throws Exception {
        String raw = received.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(raw).as("serwer nie odesłał ramki w %d ms", TIMEOUT_MS).isNotNull();
        return JSON.readTree(raw);
    }

    CloseStatus awaitClose() throws Exception {
        return closed.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        received.add(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closed.complete(status);
    }

    @Override
    public void close() throws Exception {
        if (session != null && session.isOpen()) {
            session.close();
        }
    }
}
