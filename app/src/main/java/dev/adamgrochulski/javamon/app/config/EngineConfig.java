package dev.adamgrochulski.javamon.app.config;

import dev.adamgrochulski.javamon.engine.model.MoveDex;
import dev.adamgrochulski.javamon.engine.model.PokemonDex;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Katalogi silnika jako singletony. Ładowanie JSON-a (1.2 MB pokedexu)
 * dzieje się raz, przy starcie — nie przy każdym żądaniu.
 * <p>
 * To jedyne miejsce, w którym Spring dotyka silnika. Same klasy silnika
 * nie mają adnotacji i nie wiedzą, że działają w kontenerze.
 */
@Configuration
public class EngineConfig {

    @Bean
    PokemonDex pokemonDex() {
        return new PokemonDex();
    }

    @Bean
    MoveDex moveDex() {
        return new MoveDex();
    }
}
