package dev.adamgrochulski.javamon.app.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Strumień BattleEvent jednej walki jako jeden dokument jsonb. Osobna tabela,
 * a nie kolumna na battles — inaczej każde listowanie historii ciągnęłoby
 * blob, którego nie pokazuje.
 */
@Entity
@Table(name = "battle_replays")
public class BattleReplay implements Persistable<UUID> {

    @Id
    @Column(name = "battle_id")
    private UUID battleId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String events;

    @Transient
    private boolean isNew = true;

    protected BattleReplay() {
    }

    public BattleReplay(UUID battleId, String events) {
        this.battleId = battleId;
        this.events = events;
    }

    public String getEvents() { return events; }

    @Override
    public UUID getId() { return battleId; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }
}
