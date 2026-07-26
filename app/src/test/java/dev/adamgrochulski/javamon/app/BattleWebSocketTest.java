package dev.adamgrochulski.javamon.app;

import dev.adamgrochulski.javamon.app.ws.WsClose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class BattleWebSocketTest extends AbstractApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    /** bearerFor daje gotowy nagłówek; tu potrzebny jest sam token. */
    private String tokenFor(String username) throws Exception {
        return bearerFor(username).substring("Bearer ".length());
    }

    private static String authPayload(String token) {
        return "{\"token\":\"%s\"}".formatted(token);
    }

    private static HttpHeaders handshakeHeaders(String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Upgrade", "websocket");
        headers.add("Connection", "Upgrade");
        headers.add("Sec-WebSocket-Version", "13");
        headers.add("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==");
        headers.add("Origin", origin);
        return headers;
    }

    @Test
    void poprawnyTokenDajeAuthOk() throws Exception {
        String token = tokenFor("ws-ash");

        try (WsTestClient client = WsTestClient.connect(port)) {
            client.send("AUTH", authPayload(token));

            var frame = client.nextFrame();
            assertThat(frame.get("type").asText()).isEqualTo("AUTH_OK");
            assertThat(frame.get("payload").get("username").asText()).isEqualTo("ws-ash");
            assertThat(frame.get("payload").get("guest").asBoolean()).isFalse();
            assertThat(frame.has("seq")).as("ramki spoza walki nie mają seq").isFalse();
        }
    }

    @Test
    void przedAuthKazdaInnaRamkaJestOdrzucana() throws Exception {
        try (WsTestClient client = WsTestClient.connect(port)) {
            client.send("PING", null);

            var frame = client.nextFrame();
            assertThat(frame.get("type").asText()).isEqualTo("ERROR");
            assertThat(frame.get("payload").get("code").asText()).isEqualTo("unauthenticated");
        }
    }

    @Test
    void zlyTokenZamykaPolaczenieKodem4401() throws Exception {
        try (WsTestClient client = WsTestClient.connect(port)) {
            client.send("AUTH", authPayload("to-nie-jest-token"));

            assertThat(client.awaitClose().getCode()).isEqualTo(WsClose.UNAUTHORIZED);
        }
    }

    /** Timeout w profilu test to 1 s — patrz javamon.ws.auth-timeout. */
    @Test
    void brakAuthZamykaPolaczenieKodem4408() throws Exception {
        try (WsTestClient client = WsTestClient.connect(port)) {
            assertThat(client.awaitClose().getCode()).isEqualTo(WsClose.AUTH_TIMEOUT);
        }
    }

    @Test
    void drugieLogowanieRozlaczaPierwszePolaczenie() throws Exception {
        String token = tokenFor("ws-dwie-karty");

        try (WsTestClient first = WsTestClient.connect(port);
             WsTestClient second = WsTestClient.connect(port)) {

            first.send("AUTH", authPayload(token));
            assertThat(first.nextFrame().get("type").asText()).isEqualTo("AUTH_OK");

            second.send("AUTH", authPayload(token));
            assertThat(second.nextFrame().get("type").asText()).isEqualTo("AUTH_OK");

            assertThat(first.awaitClose().getCode()).isEqualTo(WsClose.SESSION_REPLACED);
        }
    }

    @Test
    void pingDajePong() throws Exception {
        try (WsTestClient client = WsTestClient.connect(port)) {
            client.send("AUTH", authPayload(tokenFor("ws-pingujacy")));
            client.nextFrame();

            client.send("PING", null);
            assertThat(client.nextFrame().get("type").asText()).isEqualTo("PONG");
        }
    }

    @Test
    void polamanyJsonNieZrywaPolaczenia() throws Exception {
        try (WsTestClient client = WsTestClient.connect(port)) {
            client.send("AUTH", authPayload(tokenFor("ws-niechlujny")));
            client.nextFrame();

            client.sendRaw("{to nie jest json");

            var frame = client.nextFrame();
            assertThat(frame.get("type").asText()).isEqualTo("ERROR");
            assertThat(frame.get("payload").get("code").asText()).isEqualTo("bad_frame");

            // Kluczowa część: sesja żyje dalej.
            client.send("PING", null);
            assertThat(client.nextFrame().get("type").asText()).isEqualTo("PONG");
        }
    }

    @Test
    void nieznanyTypRamkiDajeBadFrame() throws Exception {
        try (WsTestClient client = WsTestClient.connect(port)) {
            client.send("AUTH", authPayload(tokenFor("ws-wymyslacz")));
            client.nextFrame();

            client.send("TANIEC_ZWYCIESTWA", null);
            assertThat(client.nextFrame().get("payload").get("code").asText()).isEqualTo("bad_frame");
        }
    }

    @Test
    void znanaAleNiegotowaRamkaDajeNotImplemented() throws Exception {
        try (WsTestClient client = WsTestClient.connect(port)) {
            client.send("AUTH", authPayload(tokenFor("ws-niecierpliwy")));
            client.nextFrame();

            client.send("MOVE", "{\"battleId\":\"0f0a0000-0000-0000-0000-000000000000\","
                    + "\"turn\":1,\"moveIndex\":0}");
            assertThat(client.nextFrame().get("payload").get("code").asText())
                    .isEqualTo("not_implemented");
        }
    }

    @Test
    void ramkaAuthBezPayloaduDajeBadFrame() throws Exception {
        try (WsTestClient client = WsTestClient.connect(port)) {
            client.send("AUTH", null);
            assertThat(client.nextFrame().get("payload").get("code").asText()).isEqualTo("bad_frame");
        }
    }

    /**
     * Przeglądarka nie stosuje polityki same-origin do WebSocketów, więc jedyną
     * barierą jest lista origin w WebSocketConfig. Handshake to zwykły HTTP GET,
     * więc da się go sprawdzić bez klienta WS — odrzucenie następuje przed
     * upgrade'em, czyli zwykłym statusem HTTP.
     */
    @Test
    void handshakeZObcegoOriginJestOdrzucany() {
        ResponseEntity<String> response = rest.exchange("/ws/battle", HttpMethod.GET,
                new HttpEntity<>(handshakeHeaders("http://zlodziej.example")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Kontrapunkt dla testu wyżej: ten sam handshake z dozwolonego origin nie
     * jest odrzucany na kontroli Origin. Statusu 101 tu nie zobaczymy — zwykły
     * klient HTTP nie potrafi przeprowadzić upgrade'u, więc Tomcat kończy
     * czterysetką. To, że pełny handshake naprawdę działa, pokazują pozostałe
     * testy w tej klasie, które łączą się prawdziwym klientem WebSocket.
     */
    @Test
    void dozwolonyOriginPrzechodziKontroleOrigin() {
        // http://localhost:5173 pochodzi z application-test.yml
        ResponseEntity<String> response = rest.exchange("/ws/battle", HttpMethod.GET,
                new HttpEntity<>(handshakeHeaders("http://localhost:5173")), String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
