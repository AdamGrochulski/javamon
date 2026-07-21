package dev.adamgrochulski.javamon.app.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinishedBattleRepository extends JpaRepository<FinishedBattle, UUID> {

    List<FinishedBattle> findByPlayer1IdOrPlayer2IdOrderByFinishedAtDesc(UUID p1, UUID p2);
}
