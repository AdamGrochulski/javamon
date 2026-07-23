package dev.adamgrochulski.javamon.app.api;

import dev.adamgrochulski.javamon.app.api.PokedexDtos.MoveDto;
import dev.adamgrochulski.javamon.app.api.PokedexDtos.SpeciesSummary;
import dev.adamgrochulski.javamon.engine.model.MoveDex;
import dev.adamgrochulski.javamon.engine.model.PokemonDex;
import dev.adamgrochulski.javamon.engine.model.Species;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/** Dane statyczne, te same dla wszystkich — dostępne bez logowania. */
@RestController
@RequestMapping("/api/pokemon")
public class PokedexController {

    private final PokemonDex pokemonDex;
    private final MoveDex moveDex;

    PokedexController(PokemonDex pokemonDex, MoveDex moveDex) {
        this.pokemonDex = pokemonDex;
        this.moveDex = moveDex;
    }

    @GetMapping
    public List<SpeciesSummary> list() {
        return pokemonDex.all().stream()
                .sorted(Comparator.comparingInt(Species::num))
                .map(SpeciesSummary::from)
                .toList();
    }

    @GetMapping("/{id}")
    public SpeciesSummary get(@PathVariable String id) {
        return SpeciesSummary.from(species(id));
    }

    /** Learnset z pełnymi danymi ruchów — pod team builder. */
    @GetMapping("/{id}/moves")
    public List<MoveDto> moves(@PathVariable String id) {
        return species(id).learnset().stream()
                .sorted()
                .map(name -> MoveDto.from(moveDex.get(name), moveDex.isSimplified(name)))
                .toList();
    }

    private Species species(String id) {
        // PokemonDex.get rzuca IllegalArgumentException; zamieniamy na 404,
        // żeby literówka w URL-u nie wyglądała jak awaria serwera.
        if (!pokemonDex.has(id)) {
            throw new ApiExceptions.NotFound("Nieznany gatunek: " + id);
        }
        return pokemonDex.get(id);
    }
}
