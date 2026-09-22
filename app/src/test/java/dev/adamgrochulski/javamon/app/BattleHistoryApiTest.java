package dev.adamgrochulski.javamon.app;

import dev.adamgrochulski.javamon.app.battle.*;
import dev.adamgrochulski.javamon.app.persistence.BattleReplayRepository;
import dev.adamgrochulski.javamon.app.persistence.FinishedBattleRepository;
import dev.adamgrochulski.javamon.app.persistence.Trainer;
import dev.adamgrochulski.javamon.app.persistence.TrainerRepository;
import dev.adamgrochulski.javamon.engine.battle.Player;
import dev.adamgrochulski.javamon.engine.model.PokemonDex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Zakończona walka ma zostać w Postgresie: wynik, replay i przeliczony rating. */
class BattleHistoryApiTest extends AbstractApiTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    BattleSessionService sessions;

    @Autowired
    TrainerRepository trainers;

    @Autowired
    FinishedBattleRepository battles;

    @Autowired
    BattleReplayRepository replays;

    @Autowired
    PokemonDex dex;

    @Test
    void poddana_walka_ladnie_w_bazie_i_przelicza_rating() throws Exception {
        String ashBearer = bearerFor("hist-ash" + COUNTER.incrementAndGet());
        String garyBearer = bearerFor("hist-gary" + COUNTER.incrementAndGet());
        UUID ash = idOf(ashBearer);
        UUID gary = idOf(garyBearer);

        BattleSession session = sessions.create(
                new Participant(ash, "ash", 1000), roster(),
                new Participant(gary, "gary", 1000), roster());

        Optional<TurnOutcome> outcome = sessions.forfeit(session.id(), ash);

        assertThat(outcome).isPresent();
        BattleSummary summary = outcome.get().summary();
        assertThat(summary.winner()).isEqualTo(Player.P2);
        assertThat(summary.ratingAfter().get(Player.P1)).isLessThan(1000);
        assertThat(summary.ratingAfter().get(Player.P2)).isGreaterThan(1000);

        assertThat(battles.findById(session.id())).isPresent();
        assertThat(replays.findById(session.id())).isPresent();
        assertThat(trainers.findById(gary).map(Trainer::getRating)).hasValueSatisfying(
                rating -> assertThat(rating).isGreaterThan(1000));

        mockMvc.perform(get("/api/battles/history").header("Authorization", ashBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].battleId").value(session.id().toString()))
                .andExpect(jsonPath("$[0].won").value(false))
                .andExpect(jsonPath("$[0].result").value("FORFEIT"));
    }

    @Test
    void replay_jest_publiczny_po_uuid() throws Exception {
        String ashBearer = bearerFor("replay-ash" + COUNTER.incrementAndGet());
        String garyBearer = bearerFor("replay-gary" + COUNTER.incrementAndGet());

        BattleSession session = sessions.create(
                new Participant(idOf(ashBearer), "ash", 1000), roster(),
                new Participant(idOf(garyBearer), "gary", 1000), roster());
        sessions.forfeit(session.id(), idOf(ashBearer));

        // Bez nagłówka Authorization: kto ma link, ten ogląda.
        mockMvc.perform(get("/api/battles/" + session.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("FORFEIT"))
                .andExpect(jsonPath("$.events[0].type").value("FORFEIT"))
                .andExpect(jsonPath("$.events[1].type").value("BATTLE_END"));
    }

    @Test
    void walka_z_gosciem_nie_rusza_ratingu() throws Exception {
        String ashBearer = bearerFor("guest-host" + COUNTER.incrementAndGet());
        UUID ash = idOf(ashBearer);

        String guestBody = mockMvc.perform(post("/api/auth/guest"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID guest = idOf("Bearer " + objectMapper.readTree(guestBody).get("token").asText());

        BattleSession session = sessions.create(
                new Participant(ash, "ash", 1000), roster(),
                new Participant(guest, "guest", 1000), roster());
        sessions.forfeit(session.id(), guest);

        assertThat(trainers.findById(ash).map(Trainer::getRating)).contains(1000);
    }

    @Test
    void leaderboard_pomija_gosci() throws Exception {
        mockMvc.perform(post("/api/auth/guest")).andExpect(status().isOk());

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[*].username", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.startsWith("gosc")))));
    }

    private UUID idOf(String bearer) {
        String token = bearer.substring("Bearer ".length());
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]));
        return UUID.fromString(payload.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1"));
    }

    private List<MonSnapshot> roster() {
        return List.of("charizard", "blastoise", "venusaur", "pikachu", "gengar", "alakazam").stream()
                .map(id -> new MonSnapshot(id, 50,
                        dex.get(id).learnset().stream().sorted().limit(4).toList()))
                .toList();
    }
}
