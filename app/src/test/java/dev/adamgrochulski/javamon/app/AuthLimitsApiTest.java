package dev.adamgrochulski.javamon.app;

import dev.adamgrochulski.javamon.app.auth.GuestCleanupJob;
import dev.adamgrochulski.javamon.app.persistence.Trainer;
import dev.adamgrochulski.javamon.app.persistence.TrainerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Limity z docs/security.md: B1 (próby logowania) i B2 (konta gościa). */
@TestPropertySource(properties = {
        "javamon.security.login-failures-per-user=3",
        "javamon.security.login-failures-per-ip=100",
        "javamon.security.guests-per-ip=2"
})
@Import(GuestPurge.class)
class AuthLimitsApiTest extends AbstractApiTest {

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    TrainerRepository trainers;

    @Autowired
    GuestCleanupJob cleanup;

    @Autowired
    GuestPurge purge;

    @Test
    void po_trzech_pomylkach_konto_jest_zablokowane() throws Exception {
        bearerFor("limit-ofiara");
        redis.delete("auth:fail:user:limit-ofiara");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials("limit-ofiara", "zlehaslo123")))
                    .andExpect(status().isUnauthorized());
        }

        // Czwarta próba nie dochodzi już do porównania hasła, także z prawidłowym.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("limit-ofiara", "tajnehaslo123")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void udane_logowanie_zeruje_licznik_konta() throws Exception {
        bearerFor("limit-szczesciarz");
        redis.delete("auth:fail:user:limit-szczesciarz");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("limit-szczesciarz", "zlehaslo123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("limit-szczesciarz", "tajnehaslo123")))
                .andExpect(status().isOk());

        assertThat(redis.opsForValue().get("auth:fail:user:limit-szczesciarz")).isNull();
    }

    @Test
    void trzecie_konto_goscia_z_tego_samego_adresu_odrzucone() throws Exception {
        redis.delete("auth:guest:ip:127.0.0.1");

        mockMvc.perform(post("/api/auth/guest")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/guest")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/guest")).andExpect(status().isTooManyRequests());

        redis.delete("auth:guest:ip:127.0.0.1");
    }

    @Test
    void sprzatanie_usuwa_goscia_bez_walk() {
        redis.delete("auth:guest:ip:127.0.0.1");
        Trainer guest = trainers.save(Trainer.guest("Gość-do-sprzątnięcia"));

        // Retencja domyślna to 24 h, a konto powstało przed chwilą, więc ma zostać.
        cleanup.removeStaleGuests();
        assertThat(trainers.findById(guest.getId())).isPresent();

        // Retencja liczona od "teraz minus okno", więc test przesuwa okno, a nie zegar.
        // Nie dokładnie 1: kontener bazy jest wspólny, więc goście z innych testów też wylecą.
        assertThat(purge.removeOlderThan(java.time.Instant.now().plusSeconds(60))).isPositive();
        assertThat(trainers.findById(guest.getId())).isEmpty();
    }
}
