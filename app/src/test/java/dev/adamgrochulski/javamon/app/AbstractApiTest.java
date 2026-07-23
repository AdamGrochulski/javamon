package dev.adamgrochulski.javamon.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

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
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractApiTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
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
}
