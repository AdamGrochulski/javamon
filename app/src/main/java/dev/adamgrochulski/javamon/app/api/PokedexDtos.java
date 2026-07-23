package dev.adamgrochulski.javamon.app.api;

import dev.adamgrochulski.javamon.engine.model.Move;
import dev.adamgrochulski.javamon.engine.model.Species;
import dev.adamgrochulski.javamon.engine.model.Stats;

import java.util.List;

/**
 * Kształt odpowiedzi API. Świadomie osobny od modelu silnika: zmiana pola
 * w Species nie ma prawa po cichu zmienić kontraktu z frontendem.
 * Enumy wypuszczamy jako String — front nie zna naszych typów Javy.
 */
public final class PokedexDtos {

    private PokedexDtos() {
    }

    public record StatsDto(int hp, int attack, int defense,
                           int specialAttack, int specialDefense, int speed) {

        static StatsDto from(Stats stats) {
            return new StatsDto(stats.hp(), stats.attack(), stats.defense(),
                    stats.specialAttack(), stats.specialDefense(), stats.speed());
        }
    }

    /** Pozycja listy — bez learnsetu, bo 1025 × ~76 nazw to megabajty. */
    public record SpeciesSummary(String id, String name, int num,
                                 String primaryType, String secondaryType, StatsDto base) {

        static SpeciesSummary from(Species species) {
            return new SpeciesSummary(
                    species.id(), species.name(), species.num(),
                    species.primary().name(),
                    species.secondary() == null ? null : species.secondary().name(),
                    StatsDto.from(species.base()));
        }
    }

    public record MoveDto(String name, String type, String category,
                          int power, int accuracy, int pp, int priority, boolean simplified) {

        static MoveDto from(Move move, boolean simplified) {
            return new MoveDto(move.name(), move.type().name(), move.category().name(),
                    move.power(), move.accuracy(), move.pp(), move.priority(), simplified);
        }
    }
}