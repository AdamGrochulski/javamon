package dev.adamgrochulski.javamon.app.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_id", nullable = false)
    private Trainer trainer;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("slotIndex ASC")
    private List<TeamSlot> slots = new ArrayList<>();

    protected Team() {
    }

    public Team(Trainer trainer,  String name) {
        this.trainer = trainer;
        this.name = name;
        this.createdAt = Instant.now();
    }

    /**
     * Rozdzielone od {@link #addSlots} celowo: między jednym a drugim serwis
     * musi wymusić flush, bo Hibernate wykonuje INSERT-y przed DELETE-ami
     * i nowy slot_index zderzyłby się ze starym, jeszcze nieusuniętym.
     */
    public void clearSlots() {
        slots.clear();
    }

    public void addSlots(List<TeamSlot> newSlots) {
        newSlots.forEach(this::addSlot);
    }

    private void addSlot(TeamSlot slot) {
        slot.assignTo(this);
        slots.add(slot);
    }

    public UUID getId() { return id; }
    public Trainer getTrainer() { return trainer; }
    public String getName() { return name; }
    public List<TeamSlot> getSlots() { return List.copyOf(slots); }
    public void rename(String newName) { this.name = newName; }
}
