package dev.adamgrochulski.javamon.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punkt wejścia warstwy aplikacyjnej: REST, WebSocket, persystencja.
 *
 * <p>Skanowanie komponentów obejmuje wyłącznie ten pakiet — klasy silnika
 * ({@code ...javamon.engine}) są zwykłym Javowym kodem, nie beanami.
 */
@SpringBootApplication
public class JavamonApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavamonApplication.class, args);
    }
}
