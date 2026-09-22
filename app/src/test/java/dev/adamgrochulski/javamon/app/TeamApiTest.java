package dev.adamgrochulski.javamon.app;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamApiTest extends AbstractApiTest {
    /**
     * Ruchy bierzemy z learnsetu, zamiast wpisywać na sztywno — inaczej
     * regeneracja pokedexu potrafiłaby wywalić testy z powodu niezwiązanego
     * ze zmianą w kodzie.
     */

    @Test
    void tworzyDruzyneZSzesciomaSlotami() throws Exception {
        String bearer = bearerFor("tworca");

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTeam("Pierwsza").toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.slots.length()").value(6))
                .andExpect(jsonPath("$.slots[0].slotIndex").value(0))
                .andExpect(jsonPath("$.slots[5].slotIndex").value(5));
    }

    /**
     * Regresja: podmiana slotów kasuje stare i wstawia nowe z tymi samymi
     * indeksami. Hibernate wykonuje INSERT-y przed DELETE-ami, więc bez flusha
     * między jednym a drugim wywala się UNIQUE (team_id, slot_index).
     */
    @Test
    void aktualizujeDruzyneBezKolizjiIndeksowSlotow() throws Exception {
        String bearer = bearerFor("aktualizator");
        String id = createTeam(bearer, "Do zmiany");

        ObjectNode changed = validTeam("Po zmianie");
        ((ObjectNode) changed.get("slots").get(0)).put("level", 100);

        mockMvc.perform(put("/api/teams/" + id)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changed.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Po zmianie"))
                .andExpect(jsonPath("$.slots.length()").value(6))
                .andExpect(jsonPath("$.slots[0].level").value(100));
    }

    @Test
    void odrzucaRuchSpozaLearnsetu() throws Exception {
        String bearer = bearerFor("oszust");

        ObjectNode team = validTeam("Nielegalna");
        ObjectNode first = (ObjectNode) team.get("slots").get(0);
        first.putArray("moves").add("Hydro Pump");   // Charizard tego nie uczy

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(team.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("invalid_team"));
    }

    @Test
    void odrzucaDwaTeSameGatunki() throws Exception {
        String bearer = bearerFor("klonujacy");

        ObjectNode team = validTeam("Klony");
        ArrayNode slots = (ArrayNode) team.get("slots");
        slots.set(1, slot("charizard", 50, legalMoves("charizard", 4)));

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(team.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("invalid_team"));
    }

    @Test
    void odrzucaNieznanyGatunek() throws Exception {
        String bearer = bearerFor("wymyslacz");

        ObjectNode team = validTeam("Zmyslona");
        ((ObjectNode) team.get("slots").get(0)).put("speciesId", "niematakiego");

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(team.toString()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void odrzucaNiepelnaDruzyne() throws Exception {
        String bearer = bearerFor("skapiec");

        ObjectNode team = validTeam("Piatka");
        ((ArrayNode) team.get("slots")).remove(5);

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(team.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.slots").isNotEmpty());
    }

    @Test
    void odrzucaPoziomPozaZakresem() throws Exception {
        String bearer = bearerFor("przesadny");

        ObjectNode team = validTeam("Przepakowana");
        ((ObjectNode) team.get("slots").get(0)).put("level", 999);

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(team.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['slots[0].level']").isNotEmpty());
    }

    @Test
    void nieWidziCudzejDruzyny() throws Exception {
        String wlasciciel = bearerFor("wlasciciel");
        String obcy = bearerFor("obcy");
        String id = createTeam(wlasciciel, "Prywatna");

        mockMvc.perform(get("/api/teams/" + id).header("Authorization", obcy))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/teams/" + id).header("Authorization", obcy))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/teams").header("Authorization", obcy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void usuwaWlasnaDruzyne() throws Exception {
        String bearer = bearerFor("kasownik");
        String id = createTeam(bearer, "Do kasacji");

        mockMvc.perform(delete("/api/teams/" + id).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/" + id).header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void druganDruzynaOTejSamejNazwieDaje409() throws Exception {
        String bearer = bearerFor("nazewnik");
        createTeam(bearer, "Ta sama");

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTeam("Ta sama").toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    void inniGraczeMogaMiecDruzyneOTejSamejNazwie() throws Exception {
        createTeam(bearerFor("pierwszy-nazewnik"), "Wspólna nazwa");

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearerFor("drugi-nazewnik"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTeam("Wspólna nazwa").toString()))
                .andExpect(status().isCreated());
    }

    @Test
    void nieistniejacaDruzynaDaje404() throws Exception {
        String bearer = bearerFor("szukajacy");

        mockMvc.perform(get("/api/teams/" + UUID.randomUUID()).header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    // --- regresje kontraktu błędów: bez ResponseEntityExceptionHandler
    //     wszystkie trzy wracały jako 500 ---

    @Test
    void nieznanaSciezkaDaje404() throws Exception {
        mockMvc.perform(get("/api/nie-ma-takiego").header("Authorization", bearerFor("bladzacy")))
                .andExpect(status().isNotFound());
    }

    @Test
    void polamanyJsonDaje400() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearerFor("niechlujny"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{niepoprawny"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zlaMetodaDaje405() throws Exception {
        mockMvc.perform(post("/api/pokemon").header("Authorization", bearerFor("pomylony")))
                .andExpect(status().isMethodNotAllowed());
    }
}
