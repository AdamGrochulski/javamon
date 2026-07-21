package dev.adamgrochulski.javamon.app.persistence;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "battles")
public class FinishedBattle implements Persistable<UUID> {

    /** Nadawane przez aplikację - to samo id, którym walka żyła w Redisie. */
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player1_id", nullable = false)
    private Trainer player1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player2_id", nullable = false)
    private Trainer player2;

    /** Snapshot nazw z chwili walki — zmiana nicka nie przepisuje historii. */
    @Column(name = "player1_name", nullable = false, length = 32)
    private String player1Name;

    @Column(name = "player2_name", nullable = false, length = 32)
    private String player2Name;

    @Column(name = "player1_rating_before", nullable = false)
    private int player1RatingBefore;

    @Column(name = "player2_rating_before", nullable = false)
    private int player2RatingBefore;

    @Column(name = "player1_rating_after", nullable = false)
    private int player1RatingAfter;

    @Column(name = "player2_rating_after", nullable = false)
    private int player2RatingAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Trainer winner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BattleResult result;

    @Column(nullable = false)
    private int turns;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Transient
    private boolean isNew = true;

    protected FinishedBattle() {
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }
}
