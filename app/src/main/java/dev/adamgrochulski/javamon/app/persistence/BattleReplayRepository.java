package dev.adamgrochulski.javamon.app.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BattleReplayRepository extends JpaRepository<BattleReplay, UUID> {
}
