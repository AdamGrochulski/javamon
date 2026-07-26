package dev.adamgrochulski.javamon.app.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Infrastruktura WebSocketu, osobno od rejestracji handlera.
 * <p>
 * Te beany nie mogą mieszkać w {@link WebSocketConfig}: żeby Spring wywołał
 * metodę @Bean, musi najpierw zbudować klasę konfiguracyjną — a ta bierze
 * w konstruktorze handler, który z kolei potrzebuje TaskSchedulera. Cykl.
 */
@Configuration
public class WsInfraConfig {

    /**
     * Limity kontenera. Ramka protokołu to kilka kilobajtów; większa oznacza
     * błąd albo próbę wyczerpania pamięci. Binarnych nie używamy wcale.
     */
    @Bean
    ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(8 * 1024);
        container.setMaxBinaryMessageBufferSize(1024);
        container.setMaxSessionIdleTimeout(120_000L);
        return container;
    }

    /**
     * Boot tworzy TaskScheduler dopiero przy @EnableScheduling, a my potrzebujemy
     * go bez włączania skanowania @Scheduled. Krok 7 dołoży tu timery tur.
     */
    @Bean
    TaskScheduler wsTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-sched-");
        scheduler.setRemoveOnCancelPolicy(true);    // anulowane timeouty nie zalegają w kolejce
        return scheduler;
    }
}
