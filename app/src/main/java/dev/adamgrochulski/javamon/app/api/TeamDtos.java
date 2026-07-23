package dev.adamgrochulski.javamon.app.api;

import dev.adamgrochulski.javamon.app.persistence.Team;
import dev.adamgrochulski.javamon.app.persistence.TeamSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class TeamDtos {

    private TeamDtos() {
    }

    public record SlotRequest(
            @NotBlank String speciesId,
            @Min(1) @Max(100) int level,
            @NotNull @Size(min = 1, max = 4) List<@NotBlank String> moves) {
    }

    public record TeamRequest(
            @NotBlank @Size(max = 64) String name,
            // Adnotacja @Valid na elemencie listy jest konieczna — bez niej
            // ograniczenia wewnątrz SlotRequest nie są sprawdzane.
            @NotNull @Size(min = 6, max = 6) List<@Valid SlotRequest> slots) {
    }

    public record SlotResponse(int slotIndex, String speciesId, int level, List<String> moves) {

        static SlotResponse from(TeamSlot slot) {
            return new SlotResponse(slot.getSlotIndex(), slot.getSpeciesId(),
                    slot.getLevel(), slot.getMoves());
        }
    }

    public record TeamResponse(UUID id, String name, List<SlotResponse> slots) {

        static TeamResponse from(Team team) {
            return new TeamResponse(team.getId(), team.getName(),
                    team.getSlots().stream().map(SlotResponse::from).toList());
        }
    }
}