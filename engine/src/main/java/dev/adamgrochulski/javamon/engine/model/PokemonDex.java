package dev.adamgrochulski.javamon.engine.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Katalog gatunków wczytany z /pokedex.json. Dane oddzielone od kodu, jak MoveDex
 * i TypeChart — dodanie gatunku to wiersz w JSON, zero zmian w silniku.
 * <p>
 * W przeciwieństwie do MoveDex nie ma tu pośredniego DTO: kształt JSON-a
 * odpowiada 1:1 rekordowi Species, więc Jackson mapuje go wprost.
 */
public class PokemonDex {

    private final Map<String, Species> byId;
    private final Map<String, Species> byName;

    public PokemonDex() {
        Map<String, Species> ids = new LinkedHashMap<>();
        Map<String, Species> names = new LinkedHashMap<>();
        for (Species species : load()) {
            if (ids.putIfAbsent(species.id(), species) != null) {
                throw new IllegalStateException("Zduplikowane id w pokedex.json: " + species.id());
            }
            if (names.putIfAbsent(species.name(), species) != null) {
                throw new IllegalStateException("Zduplikowana nazwa w pokedex.json: " + species.name());
            }
        }
        this.byId = Map.copyOf(ids);
        this.byName = Map.copyOf(names);
    }

    /** Gatunek po id ('charizard'). Rzuca, gdy nieznany — warstwa API waliduje wcześniej. */
    public Species get(String id) {
        Species species = byId.get(id);
        if (species == null) {
            throw new IllegalArgumentException("Nieznany gatunek: " + id);
        }
        return species;
    }

    public Species byName(String name) {
        Species species = byName.get(name);
        if (species == null) {
            throw new IllegalArgumentException("Nieznany gatunek: " + name);
        }
        return species;
    }

    public boolean has(String id) { return byId.containsKey(id); }

    public Set<String> ids() { return byId.keySet(); }

    public Collection<Species> all() { return byId.values(); }

    public int size() { return byId.size(); }

    /**
     * Sprawdza, czy każdy ruch z każdego learnsetu istnieje w podanym MoveDex.
     * Świadomie osobna metoda, nie walidacja w konstruktorze — dex gatunków
     * nie musi zależeć od dexu ruchów, żeby się załadować. Woła to test.
     */
    public void validateLearnsets(MoveDex moves) {
        for (Species species : byId.values()) {
            for (String move : species.learnset()) {
                if (!moves.has(move)) {
                    throw new IllegalStateException(
                            species.name() + ": nieznany ruch w learnsecie: " + move);
                }
            }
        }
    }

    private static Species[] load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = PokemonDex.class.getResourceAsStream("/pokedex.json")) {
            if (in == null) {
                throw new IllegalStateException("Brak pokedex.json na classpath");
            }
            return mapper.readValue(in, Species[].class);
        } catch (IOException ex) {
            throw new UncheckedIOException("Błąd czytania pokedex.json", ex);
        }
    }
}
