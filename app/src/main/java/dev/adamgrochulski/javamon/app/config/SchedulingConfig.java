package dev.adamgrochulski.javamon.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Włącza @Scheduled. Zadania idą na istniejący TaskScheduler z WsInfraConfig,
 * bo Spring bierze jedyny bean tego typu - nie ma potrzeby drugiej puli wątków.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
