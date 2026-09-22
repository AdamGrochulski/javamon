package dev.adamgrochulski.javamon.app;

import com.fasterxml.jackson.databind.JsonNode;
import dev.adamgrochulski.javamon.app.battle.BattleSession;
import dev.adamgrochulski.javamon.app.battle.BattleSessionService;
import dev.adamgrochulski.javamon.app.battle.Participant;
import dev.adamgrochulski.javamon.app.persistence.Team;
import dev.adamgrochulski.javamon.app.persistence.TeamSlot;
import dev.adamgrochulski.javamon.app.ws.BattleNotifier;
import dev.adamgrochulski.javamon.engine.model.PokemonDex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Pełna pętla walki przez prawdziwy socket: start, tura, poddanie. */
class BattleLoopWebSocketTest extends AbstractApiTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @LocalServerPort
    int port;

    @Autowired
    BattleSessionService sessions;

    @Autowired
    BattleNotifier notifier;

    @Autowired
    PokemonDex pokedex;

    @Test
    void start_ukrywa_lawke_przeciwnika() throws Exception {
        try (WsTestClient ash = WsTestClient.connect(port);
             WsTestClient gary = WsTestClient.connect(port)) {

            startBattle(ash, gary);

            JsonNode start = ash.nextFrame();

            assertThat(start.get("type").asText()).isEqualTo("BATTLE_START");
            assertThat(start.get("seq").asLong()).isEqualTo(1);
            assertThat(start.at("/payload/yourTeam").size()).isEqualTo(6);
            assertThat(start.at("/payload/opponentActive/hpPercent").asInt()).isEqualTo(100);
            assertThat(start.at("/payload/opponentActive/maxHp").isMissingNode()).isTrue();
            // Lapras siedzi na ławce przeciwnika i nie ma prawa pojawić się w ramce.
            assertThat(start.toString()).doesNotContain("Lapras");
        }
    }

    @Test
    void tura_rozlicza_sie_po_obu_akcjach() throws Exception {
        try (WsTestClient ash = WsTestClient.connect(port);
             WsTestClient gary = WsTestClient.connect(port)) {

            BattleSession session = startBattle(ash, gary);
            skipFrames(ash, 2);
            skipFrames(gary, 2);

            move(ash, session, 1, 0);
            move(gary, session, 1, 0);

            JsonNode events = ash.nextFrame();
            assertThat(events.get("type").asText()).isEqualTo("TURN_EVENTS");
            assertThat(events.get("seq").asLong()).isEqualTo(3);
            assertThat(events.at("/payload/turn").asInt()).isEqualTo(1);
            assertThat(events.at("/payload/events")).isNotEmpty();
        }
    }

    @Test
    void poddanie_konczy_walke_u_obu_graczy() throws Exception {
        try (WsTestClient ash = WsTestClient.connect(port);
             WsTestClient gary = WsTestClient.connect(port)) {

            BattleSession session = startBattle(ash, gary);
            skipFrames(ash, 2);
            skipFrames(gary, 2);

            ash.send("FORFEIT", """
                    {"battleId":"%s"}""".formatted(session.id()));

            assertThat(ash.nextFrame().get("type").asText()).isEqualTo("TURN_EVENTS");
            JsonNode end = ash.nextFrame();
            assertThat(end.get("type").asText()).isEqualTo("BATTLE_END");
            assertThat(end.at("/payload/winner").asText()).isEqualTo("P2");
            assertThat(end.at("/payload/reason").asText()).isEqualTo("forfeit");

            gary.nextFrame();
            assertThat(gary.nextFrame().get("type").asText()).isEqualTo("BATTLE_END");
        }
    }

    @Test
    void resume_dosyla_ramki_po_zerwaniu() throws Exception {
        String ashBearer = bearerFor("resume-ash" + COUNTER.incrementAndGet());
        String garyBearer = bearerFor("resume-gary" + COUNTER.incrementAndGet());
        BattleSession session;

        try (WsTestClient ash = WsTestClient.connect(port);
             WsTestClient gary = WsTestClient.connect(port)) {

            UUID ashId = authenticateWith(ash, ashBearer);
            UUID garyId = authenticateWith(gary, garyBearer);

            session = sessions.create(
                    new Participant(ashId, "ash", 1000),
                    team(List.of("charizard", "blastoise", "venusaur", "pikachu", "gengar", "alakazam")),
                    new Participant(garyId, "gary", 1000),
                    team(List.of("snorlax", "lapras", "machamp", "golem", "jolteon", "dragonite")));
            notifier.start(session);

            assertThat(ash.nextFrame().get("seq").asLong()).isEqualTo(1);
            assertThat(ash.nextFrame().get("seq").asLong()).isEqualTo(2);
        }

        try (WsTestClient ash = WsTestClient.connect(port)) {
            authenticateWith(ash, ashBearer);
            ash.send("RESUME", """
                    {"battleId":"%s","lastSeq":1}""".formatted(session.id()));

            JsonNode resent = ash.nextFrame();
            assertThat(resent.get("type").asText()).isEqualTo("REQUEST_ACTION");
            assertThat(resent.get("seq").asLong()).isEqualTo(2);
        }
    }

    private BattleSession startBattle(WsTestClient ash, WsTestClient gary) throws Exception {
        UUID ashId = authenticate(ash, "ash" + COUNTER.incrementAndGet());
        UUID garyId = authenticate(gary, "gary" + COUNTER.incrementAndGet());

        BattleSession session = sessions.create(
                new Participant(ashId, "ash", 1000),
                team(List.of("charizard", "blastoise", "venusaur", "pikachu", "gengar", "alakazam")),
                new Participant(garyId, "gary", 1042),
                team(List.of("snorlax", "lapras", "machamp", "golem", "jolteon", "dragonite")));

        notifier.start(session);
        return session;
    }

    private UUID authenticate(WsTestClient client, String username) throws Exception {
        return authenticateWith(client, bearerFor(username));
    }

    private UUID authenticateWith(WsTestClient client, String bearer) throws Exception {
        client.send("AUTH", """
                {"token":"%s"}""".formatted(bearer.substring("Bearer ".length())));

        JsonNode authOk = client.nextFrame();
        assertThat(authOk.get("type").asText()).isEqualTo("AUTH_OK");
        return UUID.fromString(authOk.at("/payload/trainerId").asText());
    }

    private void move(WsTestClient client, BattleSession session, int turn, int moveIndex) throws Exception {
        client.send("MOVE", """
                {"battleId":"%s","turn":%d,"moveIndex":%d}""".formatted(session.id(), turn, moveIndex));
    }

    private void skipFrames(WsTestClient client, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            client.nextFrame();
        }
    }

    private Team team(List<String> speciesIds) {
        Team team = new Team(null, "test");
        List<TeamSlot> slots = new ArrayList<>();
        for (int i = 0; i < speciesIds.size(); i++) {
            List<String> moves = pokedex.get(speciesIds.get(i)).learnset().stream().sorted().limit(4).toList();
            slots.add(new TeamSlot(i, speciesIds.get(i), 50, moves));
        }
        team.addSlots(slots);
        return team;
    }
}