package dev.adamgrochulski.javamon.app.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByTrainerId(UUID trainerId);

    Optional<Team> findByIdAndTrainerId(UUID id, UUID trainerId);
}
