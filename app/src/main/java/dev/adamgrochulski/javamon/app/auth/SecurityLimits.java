package dev.adamgrochulski.javamon.app.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Limity antyprzemiałowe. Z konfiguracji, żeby test nie musiał wykonać
 * dziesięciu prawdziwych logowań, a produkcja mogła zacisnąć śrubę bez deployu.
 *
 * @param loginFailuresPerUser po ilu nieudanych próbach blokujemy konto na czas okna
 * @param loginFailuresPerIp   to samo liczone po adresie; botnet obchodzi limit po nicku
 * @param guestsPerIp          ile kont gościa wolno założyć z jednego adresu w oknie
 * @param guestRetention       po jakim czasie sprzątamy nieużywane konto gościa
 */
@ConfigurationProperties(prefix = "javamon.security")
public record SecurityLimits(int loginFailuresPerUser, int loginFailuresPerIp, Duration loginWindow,
                             int guestsPerIp, Duration guestWindow, Duration guestRetention) {

    public SecurityLimits {
        if (loginFailuresPerUser <= 0) loginFailuresPerUser = 10;
        if (loginFailuresPerIp <= 0) loginFailuresPerIp = 30;
        if (loginWindow == null) loginWindow = Duration.ofMinutes(15);
        if (guestsPerIp <= 0) guestsPerIp = 5;
        if (guestWindow == null) guestWindow = Duration.ofHours(1);
        if (guestRetention == null) guestRetention = Duration.ofHours(24);
    }
}
