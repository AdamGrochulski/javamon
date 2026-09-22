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

    public FinishedBattle(UUID id, Trainer player1, Trainer player2,
                          String player1Name, String player2Name,
                          int player1RatingBefore, int player2RatingBefore,
                          int player1RatingAfter, int player2RatingAfter,
                          Trainer winner, BattleResult result, int turns,
                          Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.player1 = player1;
        this.player2 = player2;
        this.player1Name = player1Name;
        this.player2Name = player2Name;
        this.player1RatingBefore = player1RatingBefore;
        this.player2RatingBefore = player2RatingBefore;
        this.player1RatingAfter = player1RatingAfter;
        this.player2RatingAfter = player2RatingAfter;
        this.winner = winner;
        this.result = result;
        this.turns = turns;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public Trainer getPlayer1() { return player1; }
    public Trainer getPlayer2() { return player2; }
    public String getPlayer1Name() { return player1Name; }
    public String getPlayer2Name() { return player2Name; }
    public int getPlayer1RatingBefore() { return player1RatingBefore; }
    public int getPlayer2RatingBefore() { return player2RatingBefore; }
    public int getPlayer1RatingAfter() { return player1RatingAfter; }
    public int getPlayer2RatingAfter() { return player2RatingAfter; }
    public Trainer getWinner() { return winner; }
    public BattleResult getResult() { return result; }
    public int getTurns() { return turns; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }
}
