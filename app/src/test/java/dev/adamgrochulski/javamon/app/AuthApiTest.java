package dev.adamgrochulski.javamon.app;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends AbstractApiTest {

    @Test
    void rejestrujeIZwracaToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("nowy", "tajnehaslo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.guest").value(false));
    }

    @Test
    void zajetyNickDaje409ZKomunikatem() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("duplikat", "tajnehaslo123")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("duplikat", "tajnehaslo123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("username_taken"));
    }

    @Test
    void krotkieHasloDaje400ZeWskazaniemPola() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("ktos", "krotkie")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").isNotEmpty());
    }

    @Test
    void zleHasloDaje401() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("logujacy", "tajnehaslo123")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("logujacy", "zle-haslo-123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logowanieIgnorujeWielkoscLiter() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("WielkieMale", "tajnehaslo123")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("wielkiemale", "tajnehaslo123")))
                .andExpect(status().isOk());
    }

    @Test
    void gosciemMoznaWejscBezRejestracji() throws Exception {
        mockMvc.perform(post("/api/auth/guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void chronionyEndpointBezTokenuDaje401() throws Exception {
        mockMvc.perform(get("/api/teams")).andExpect(status().isUnauthorized());
    }

    @Test
    void smieciowyTokenDaje401() throws Exception {
        mockMvc.perform(get("/api/teams").header("Authorization", "Bearer nie.jest.tokenem"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pokedexJestPublicznyIMaWszystkieGatunki() throws Exception {
        mockMvc.perform(get("/api/pokemon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1025));
    }

    @Test
    void nieznanyGatunekDaje404() throws Exception {
        mockMvc.perform(get("/api/pokemon/niematakiego"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }
    @Test
    void wylogowanieUniewaznieTokenNatychmiast() throws Exception {
        String bearer = bearerFor("wylogowany");

        mockMvc.perform(get("/api/teams").header("Authorization", bearer))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer))
                .andExpect(status().isNoContent());

        // Token jest dalej poprawnie podpisany i niewygasły, a mimo to nie działa.
        mockMvc.perform(get("/api/teams").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wylogowanieBezTokenaPrzechodziCicho() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
