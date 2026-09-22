package dev.adamgrochulski.javamon.app;

import com.fasterxml.jackson.databind.JsonNode;
import dev.adamgrochulski.javamon.app.battle.BattleSession;
import dev.adamgrochulski.javamon.app.battle.BattleSessionService;
import dev.adamgrochulski.javamon.app.battle.MonSnapshot;
import dev.adamgrochulski.javamon.app.battle.Participant;
import dev.adamgrochulski.javamon.app.ws.BattleNotifier;
import dev.adamgrochulski.javamon.engine.model.PokemonDex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Timer tury przez prawdziwy socket. Osobny kontekst, bo deadline musi być krótki. */
@TestPropertySource(properties = "javamon.battle.action-timeout=PT0.5S")
class BattleTimeoutWebSocketTest extends AbstractApiTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @LocalServerPort
    int port;

    @Autowired
    BattleSessionService sessions;

    @Autowired
    BattleNotifier notifier;

    @Autowired
    PokemonDex dex;

    @Test
    void milczacy_gracz_przegrywa_po_deadlinie() throws Exception {
        try (WsTestClient ash = WsTestClient.connect(port);
             WsTestClient gary = WsTestClient.connect(port)) {

            UUID ashId = authenticate(ash);
            UUID garyId = authenticate(gary);

            BattleSession session = sessions.create(
                    new Participant(ashId, "ash", 1000), roster(),
                    new Participant(garyId, "gary", 1000), roster());
            notifier.start(session);

            ash.nextFrame();    // BATTLE_START
            ash.nextFrame();    // REQUEST_ACTION
            gary.nextFrame();
            gary.nextFrame();

            // Ash gra, Gary milczy. Serwer ma sam rozstrzygnąć turę po deadlinie.
            ash.send("MOVE", """
                    {"battleId":"%s","turn":1,"moveIndex":0}""".formatted(session.id()));

            assertThat(ash.nextFrame().get("type").asText()).isEqualTo("TURN_EVENTS");
            JsonNode end = ash.nextFrame();

            assertThat(end.get("type").asText()).isEqualTo("BATTLE_END");
            assertThat(end.at("/payload/reason").asText()).isEqualTo("timeout");
            assertThat(end.at("/payload/winner").asText()).isEqualTo("P1");
        }
    }

    private UUID authenticate(WsTestClient client) throws Exception {
        String bearer = bearerFor("timeout" + COUNTER.incrementAndGet());
        client.send("AUTH", """
                {"token":"%s"}""".formatted(bearer.substring("Bearer ".length())));

        JsonNode authOk = client.nextFrame();
        assertThat(authOk.get("type").asText()).isEqualTo("AUTH_OK");
        return UUID.fromString(authOk.at("/payload/trainerId").asText());
    }

    private List<MonSnapshot> roster() {
        return ROSTER.stream()
                .map(id -> new MonSnapshot(id, 50, legalMoves(id, 4)))
                .toList();
    }
}
