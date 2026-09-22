package dev.adamgrochulski.javamon.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.adamgrochulski.javamon.engine.model.PokemonDex;
import dev.adamgrochulski.javamon.engine.model.Species;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Wspólna baza testów API. Prawdziwy Postgres w kontenerze, nie H2 — schemat
 * tworzy Flyway, więc baza musi rozumieć jsonb, uuid i indeksy częściowe.
 * <p>
 * Wzorzec singleton container: kontener startujemy sami w bloku statycznym
 * i nigdy nie zatrzymujemy, bo Spring cache'uje kontekst między klasami
 * testowymi. Adnotacje {@code @Testcontainers}/{@code @Container} ubiłyby
 * kontener po pierwszej klasie, a druga dostawałaby DataSource wskazujący
 * na martwą bazę (30 s czekania na połączenie i timeout Hikari).
 * Kontener sprząta Ryuk przy końcu JVM.
 * <p>
 * RANDOM_PORT, a nie domyślny MOCK: konfiguracja WebSocketu sięga po
 * {@code jakarta.websocket.server.ServerContainer}, którego atrapa
 * ServletContextu nie ma. MockMvc działa niezależnie od tego, czy serwer
 * faktycznie nasłuchuje, a testy WS mogą dzięki temu współdzielić ten sam
 * kontekst zamiast startować drugi kontener Postgresa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractApiTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Trwające walki żyją w Redisie, więc testy integracyjne też go potrzebują. */
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected static String credentials(String username, String password) {
        return """
                {"username":"%s","password":"%s"}""".formatted(username, password);
    }

    /** Rejestruje konto i zwraca gotową wartość nagłówka Authorization. */
    protected String bearerFor(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, "tajnehaslo123")))
                .andReturn().getResponse().getContentAsString();

        return "Bearer " + objectMapper.readTree(body).get("token").asText();
    }

    protected static final List<String> ROSTER =
            List.of("charizard", "blastoise", "venusaur", "snorlax", "gengar", "alakazam");

    @Autowired
    protected PokemonDex pokemonDex;

    /** Ruchy z learnsetu, nie wpisane na sztywno - regeneracja pokedexu nie ma wywalać testów. */
    protected List<String> legalMoves(String speciesId, int howMany) {
        Species species = pokemonDex.get(speciesId);
        return species.learnset().stream().sorted().limit(howMany).toList();
    }

    protected ObjectNode slot(String speciesId, int level, List<String> moves) {
        ObjectNode slot = objectMapper.createObjectNode();
        slot.put("speciesId", speciesId);
        slot.put("level", level);
        ArrayNode array = slot.putArray("moves");
        moves.forEach(array::add);
        return slot;
    }

    protected ObjectNode validTeam(String name) {
        ObjectNode team = objectMapper.createObjectNode();
        team.put("name", name);
        ArrayNode slots = team.putArray("slots");
        ROSTER.forEach(id -> slots.add(slot(id, 50, legalMoves(id, 4))));
        return team;
    }

    protected String createTeam(String bearer, String name) throws Exception {
        String body = mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTeam(name).toString()))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asText();
    }
}
