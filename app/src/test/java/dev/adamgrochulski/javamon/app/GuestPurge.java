package dev.adamgrochulski.javamon.app;

import dev.adamgrochulski.javamon.app.persistence.TrainerRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Zapytanie @Modifying wymaga transakcji, a test jej nie ma. Cienka otoczka, żeby ją założyć. */
@TestComponent
class GuestPurge {

    private final TrainerRepository trainers;

    GuestPurge(TrainerRepository trainers) {
        this.trainers = trainers;
    }

    @Transactional
    int removeOlderThan(Instant before) {
        return trainers.deleteStaleGuests(before);
    }
}
