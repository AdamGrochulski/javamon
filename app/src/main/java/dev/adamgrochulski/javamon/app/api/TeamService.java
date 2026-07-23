package dev.adamgrochulski.javamon.app.api;

import dev.adamgrochulski.javamon.app.api.TeamDtos.SlotRequest;
import dev.adamgrochulski.javamon.app.api.TeamDtos.TeamRequest;
import dev.adamgrochulski.javamon.app.api.TeamDtos.TeamResponse;
import dev.adamgrochulski.javamon.app.persistence.Team;
import dev.adamgrochulski.javamon.app.persistence.TeamRepository;
import dev.adamgrochulski.javamon.app.persistence.TeamSlot;
import dev.adamgrochulski.javamon.app.persistence.Trainer;
import dev.adamgrochulski.javamon.app.persistence.TrainerRepository;
import dev.adamgrochulski.javamon.engine.model.MoveDex;
import dev.adamgrochulski.javamon.engine.model.PokemonDex;
import dev.adamgrochulski.javamon.engine.model.Species;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reguły drużyny egzekwowane po stronie serwera. Klient może przysłać
 * cokolwiek — legalność movesetu nie jest jego decyzją.
 */
@Service
public class TeamService {

    private final TeamRepository teams;
    private final TrainerRepository trainers;
    private final PokemonDex pokemonDex;
    private final MoveDex moveDex;

    TeamService(TeamRepository teams, TrainerRepository trainers,
                PokemonDex pokemonDex, MoveDex moveDex) {
        this.teams = teams;
        this.trainers = trainers;
        this.pokemonDex = pokemonDex;
        this.moveDex = moveDex;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> list(UUID trainerId) {
        return teams.findByTrainerId(trainerId).stream().map(TeamResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse get(UUID trainerId, UUID teamId) {
        return TeamResponse.from(owned(trainerId, teamId));
    }

    @Transactional
    public TeamResponse create(UUID trainerId, TeamRequest request) {
        Trainer owner = trainers.findById(trainerId)
                .filter(Trainer::isActive)
                .orElseThrow(() -> new ApiExceptions.NotFound("Konto nie istnieje"));

        Team team = new Team(owner, request.name());
        team.addSlots(toSlots(request.slots()));

        // save() już tu wysyła INSERT-y: TeamSlot ma GenerationType.IDENTITY,
        // więc baza musi nadać klucz od razu, a wcześniej musi istnieć rodzic.
        // Dlatego konflikt nazwy wypada wewnątrz save(), nie dopiero we flushu.
        conflictAware(request.name(), () -> {
            teams.save(team);
            teams.flush();
        });
        return TeamResponse.from(team);
    }

    @Transactional
    public TeamResponse update(UUID trainerId, UUID teamId, TeamRequest request) {
        Team team = owned(trainerId, teamId);
        // Walidacja przed jakąkolwiek zmianą encji — 422 nie ma prawa zostawić
        // po sobie drużyny w połowie zmienionej.
        List<TeamSlot> newSlots = toSlots(request.slots());

        team.rename(request.name());
        team.clearSlots();
        // Hibernate przy flushu wykonuje INSERT-y przed DELETE-ami. Bez tego
        // wymuszenia nowy slot_index=0 zderza się ze starym, jeszcze
        // nieusuniętym — łapie to UNIQUE (team_id, slot_index).
        conflictAware(request.name(), teams::flush);
        team.addSlots(newSlots);
        conflictAware(request.name(), teams::flush);

        return TeamResponse.from(team);
    }

    /**
     * Wypycha zmiany od razu i tłumaczy naruszenie unikalności nazwy na 409.
     * Bez jawnego flusha wyjątek poleciałby dopiero przy commicie transakcji,
     * czyli poza tą metodą — i wróciłby do klienta jako 500.
     */
    private void conflictAware(String teamName, Runnable work) {
        try {
            work.run();
        } catch (DataIntegrityViolationException ex) {
            throw new ApiExceptions.Conflict("Masz już drużynę o nazwie: " + teamName);
        }
    }

    @Transactional
    public void delete(UUID trainerId, UUID teamId) {
        teams.delete(owned(trainerId, teamId));
    }

    /**
     * Właściciel jest częścią zapytania, nie sprawdzeniem po fakcie: "nie moja"
     * i "nie istnieje" dają ten sam wynik, więc nie da się zgadywać cudzych id.
     */
    private Team owned(UUID trainerId, UUID teamId) {
        return teams.findByIdAndTrainerId(teamId, trainerId)
                .orElseThrow(() -> new ApiExceptions.NotFound("Nie ma takiej drużyny"));
    }

    private List<TeamSlot> toSlots(List<SlotRequest> requests) {
        List<TeamSlot> slots = new ArrayList<>(requests.size());
        Set<String> usedSpecies = new HashSet<>();

        for (int i = 0; i < requests.size(); i++) {
            SlotRequest slot = requests.get(i);

            if (!pokemonDex.has(slot.speciesId())) {
                throw new ApiExceptions.InvalidTeam(
                        "Slot " + (i + 1) + ": nieznany gatunek " + slot.speciesId());
            }
            Species species = pokemonDex.get(slot.speciesId());

            if (!usedSpecies.add(species.id())) {
                throw new ApiExceptions.InvalidTeam(
                        "Drużyna nie może mieć dwóch tych samych gatunków: " + species.name());
            }
            validateMoves(i, species, slot.moves());
            slots.add(new TeamSlot(i, species.id(), slot.level(), slot.moves()));
        }
        return slots;
    }

    private void validateMoves(int index, Species species, List<String> moves) {
        Set<String> seen = new HashSet<>();
        for (String move : moves) {
            if (!seen.add(move)) {
                throw new ApiExceptions.InvalidTeam(
                        "Slot " + (index + 1) + ": ruch powtórzony — " + move);
            }
            if (!moveDex.has(move)) {
                throw new ApiExceptions.InvalidTeam(
                        "Slot " + (index + 1) + ": nieznany ruch " + move);
            }
            if (!species.canLearn(move)) {
                throw new ApiExceptions.InvalidTeam(
                        "Slot " + (index + 1) + ": " + species.name() + " nie uczy się " + move);
            }
        }
    }
}