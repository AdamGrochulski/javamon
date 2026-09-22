package dev.adamgrochulski.javamon.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/** Kolejka: pierwszy czeka, drugi startuje walkę dla obu. */
class MatchmakingWebSocketTest extends AbstractApiTest {

    @LocalServerPort
    int port;

    @Test
    void drugi_w_kolejce_startuje_walke() throws Exception {
        String ashBearer = bearerFor("mm-ash");
        String garyBearer = bearerFor("mm-gary");
        String ashTeam = createTeam(ashBearer, "Ash");
        String garyTeam = createTeam(garyBearer, "Gary");

        try (WsTestClient ash = WsTestClient.connect(port);
             WsTestClient gary = WsTestClient.connect(port)) {

            authenticate(ash, ashBearer);
            authenticate(gary, garyBearer);

            joinQueue(ash, ashTeam);
            assertThat(ash.nextFrame().get("type").asText()).isEqualTo("QUEUED");

            joinQueue(gary, garyTeam);

            JsonNode ashStart = ash.nextFrame();
            assertThat(ashStart.get("type").asText()).isEqualTo("BATTLE_START");
            assertThat(ashStart.at("/payload/you").asText()).isEqualTo("P1");
            assertThat(ashStart.at("/payload/opponent/username").asText()).isEqualTo("mm-gary");

            JsonNode garyStart = gary.nextFrame();
            assertThat(garyStart.get("type").asText()).isEqualTo("BATTLE_START");
            assertThat(garyStart.at("/payload/you").asText()).isEqualTo("P2");
        }
    }

    @Test
    void cudza_druzyna_odrzucona() throws Exception {
        String ashBearer = bearerFor("mm-zlodziej");
        String garyBearer = bearerFor("mm-okradziony");
        String garyTeam = createTeam(garyBearer, "Nie twoja");

        try (WsTestClient ash = WsTestClient.connect(port)) {
            authenticate(ash, ashBearer);

            joinQueue(ash, garyTeam);

            assertThat(ash.nextFrame().at("/payload/code").asText()).isEqualTo("team_invalid");
        }
    }

    @Test
    void rozlaczenie_wyjmuje_z_kolejki() throws Exception {
        String ashBearer = bearerFor("mm-znikajacy");
        String garyBearer = bearerFor("mm-cierpliwy");
        String ashTeam = createTeam(ashBearer, "Znika");
        String garyTeam = createTeam(garyBearer, "Czeka");

        try (WsTestClient ash = WsTestClient.connect(port)) {
            authenticate(ash, ashBearer);
            joinQueue(ash, ashTeam);
            ash.nextFrame();
        }

        try (WsTestClient gary = WsTestClient.connect(port)) {
            authenticate(gary, garyBearer);
            joinQueue(gary, garyTeam);

            // Gdyby duch został w kolejce, Gary dostałby BATTLE_START z nieobecnym.
            assertThat(gary.nextFrame().get("type").asText()).isEqualTo("QUEUED");
        }
    }

    @Test
    void wyjscie_z_kolejki_jest_potwierdzane() throws Exception {
        String bearer = bearerFor("mm-rezygnujacy");
        String teamId = createTeam(bearer, "Rezygnacja");

        try (WsTestClient client = WsTestClient.connect(port)) {
            authenticate(client, bearer);
            joinQueue(client, teamId);
            assertThat(client.nextFrame().get("type").asText()).isEqualTo("QUEUED");

            client.send("QUEUE_LEAVE", null);

            assertThat(client.nextFrame().get("type").asText()).isEqualTo("QUEUE_LEFT");
        }
    }

    private void authenticate(WsTestClient client, String bearer) throws Exception {
        client.send("AUTH", """
                {"token":"%s"}""".formatted(bearer.substring("Bearer ".length())));
        assertThat(client.nextFrame().get("type").asText()).isEqualTo("AUTH_OK");
    }

    private void joinQueue(WsTestClient client, String teamId) throws Exception {
        client.send("QUEUE_JOIN", """
                {"teamId":"%s"}""".formatted(teamId));
    }
}