package dev.adamgrochulski.javamon.app.ws;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** Jeden timer na walkę: uzbrajany przy każdym pytaniu o akcję, kasowany, gdy tura się rozliczy. */
@Component
public class BattleTimers {

    private final Map<UUID, ScheduledFuture<?>> armed = new ConcurrentHashMap<>();
    private final TaskScheduler scheduler;

    BattleTimers(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void arm(UUID battleId, Instant deadline, Runnable onExpiry) {
        cancel(armed.put(battleId, scheduler.schedule(onExpiry, deadline)));
    }

    public void cancel(UUID battleId) {
        cancel(armed.remove(battleId));
    }

    // false, a nie true: timer, który już wystartował, ma dokończyć rozliczanie tury.
    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }
}
