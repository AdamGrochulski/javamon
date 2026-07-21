package dev.adamgrochulski.javamon.app.persistence;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "team_slots")
public class TeamSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    /** Slug z Species.id, np. "charizard". Legalność sprawdza serwis przy zapisie. */
    @Column(name = "species_id", nullable = false, length = 32)
    private String speciesId;

    @Column(nullable = false)
    private int level;

    @Column(name = "move1", nullable = false, length = 32)
    private String move1;

    @Column(name = "move2", length = 32)
    private String move2;

    @Column(name = "move3", length = 32)
    private String move3;

    @Column(name = "move4", length = 32)
    private String move4;

    protected TeamSlot() {
    }

    public TeamSlot(int slotIndex, String speciesId, int level, List<String> moves) {
        this.slotIndex = slotIndex;
        this.speciesId = speciesId;
        this.level = level;
        this.move1 = moves.get(0);
        this.move2 = moves.size() > 1 ? moves.get(1) : null;
        this.move3 = moves.size() > 2 ? moves.get(2) : null;
        this.move4 = moves.size() > 3 ? moves.get(3) : null;
    }

    /** Cztery kolumny z powrotem jako lista - bez nulli, w kolejności slotów. */
    public List<String> getMoves() {
        List<String> moves = new ArrayList<>(4);
        for(String move : new String[]{move1, move2, move3, move4}) {
            if(move != null) {
                moves.add(move);
            }
        }
        return List.copyOf(moves);
    }

    void assignTo(Team team) { this.team = team; }

    public Long getId() { return id; }
    public int getSlotIndex() { return slotIndex; }
    public String getSpeciesId() { return speciesId; }
    public int getLevel() { return level; }
}
