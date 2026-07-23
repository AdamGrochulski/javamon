package dev.adamgrochulski.javamon.app.api;

import dev.adamgrochulski.javamon.app.api.TeamDtos.TeamRequest;
import dev.adamgrochulski.javamon.app.api.TeamDtos.TeamResponse;
import dev.adamgrochulski.javamon.app.auth.AuthenticatedTrainer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public List<TeamResponse> list(@AuthenticationPrincipal AuthenticatedTrainer me) {
        return teamService.list(me.id());
    }

    @GetMapping("/{id}")
    public TeamResponse get(@AuthenticationPrincipal AuthenticatedTrainer me,
                            @PathVariable UUID id) {
        return teamService.get(me.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse create(@AuthenticationPrincipal AuthenticatedTrainer me,
                               @Valid @RequestBody TeamRequest request) {
        return teamService.create(me.id(), request);
    }

    @PutMapping("/{id}")
    public TeamResponse update(@AuthenticationPrincipal AuthenticatedTrainer me,
                               @PathVariable UUID id,
                               @Valid @RequestBody TeamRequest request) {
        return teamService.update(me.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedTrainer me,
                       @PathVariable UUID id) {
        teamService.delete(me.id(), id);
    }
}