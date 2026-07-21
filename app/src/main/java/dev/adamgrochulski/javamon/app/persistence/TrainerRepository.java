package dev.adamgrochulski.javamon.app.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainerRepository extends JpaRepository<Trainer, UUID> {

    Optional<Trainer> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<Trainer> findTop50ByGuestFalseAndDeletedAtIsNullOrderByRatingDesc();
}
