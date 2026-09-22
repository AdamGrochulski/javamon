package dev.adamgrochulski.javamon.app.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainerRepository extends JpaRepository<Trainer, UUID> {

    Optional<Trainer> findByUsernameIgnoreCase(String username);

    /** Ranking: bez gości i bez skasowanych kont, pod indeks trainers_leaderboard_idx. */
    List<Trainer> findTop20ByGuestFalseAndDeletedAtIsNullOrderByRatingDesc();

    /**
     * Sprzątanie kont gościa. Tylko te bez rozegranych walk: resztę trzyma klucz
     * obcy z battles, a historia walki nie ma prawa zniknąć razem z kontem.
     */
    @Modifying
    @Query(value = """
            DELETE FROM trainers t
            WHERE t.guest = true
              AND t.created_at < :before
              AND NOT EXISTS (
                  SELECT 1 FROM battles b WHERE b.player1_id = t.id OR b.player2_id = t.id)
            """, nativeQuery = true)
    int deleteStaleGuests(@Param("before") Instant before);

    boolean existsByUsernameIgnoreCase(String username);

    List<Trainer> findTop50ByGuestFalseAndDeletedAtIsNullOrderByRatingDesc();
}
