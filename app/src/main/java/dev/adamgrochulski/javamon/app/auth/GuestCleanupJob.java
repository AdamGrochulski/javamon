package dev.adamgrochulski.javamon.app.auth;

import dev.adamgrochulski.javamon.app.persistence.TrainerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Codzienne sprzątanie kont gościa, które nigdy nie weszły do walki. */
@Component
public class GuestCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(GuestCleanupJob.class);

    private final TrainerRepository trainers;
    private final SecurityLimits limits;

    GuestCleanupJob(TrainerRepository trainers, SecurityLimits limits) {
        this.trainers = trainers;
        this.limits = limits;
    }

    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public int removeStaleGuests() {
        int removed = trainers.deleteStaleGuests(Instant.now().minus(limits.guestRetention()));
        if (removed > 0) {
            log.info("Usunięto {} nieużywanych kont gościa", removed);
        }
        return removed;
    }
}
