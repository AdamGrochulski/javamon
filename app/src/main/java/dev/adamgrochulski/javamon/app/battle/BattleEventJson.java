package dev.adamgrochulski.javamon.app.battle;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Eventy jako JSON: do Redisa w trakcie walki i do replaya po niej.
 * Własna kopia mappera z mixinem, bo silnik nie ma adnotacji Jacksona,
 * a ustawienia protokołu nie mają prawa zmieniać odpowiedzi REST-a.
 */
@Component
public class BattleEventJson {

    private static final TypeReference<List<BattleEvent>> LIST = new TypeReference<>() { };

    private final ObjectMapper mapper;

    BattleEventJson(ObjectMapper springMapper) {
        this.mapper = springMapper.copy();
        this.mapper.addMixIn(BattleEvent.class, BattleEventMixin.class);
    }

    /**
     * writerFor, a nie writeValueAsString: dyskryminator "type" dokleja się po typie
     * zadeklarowanym, a po wymazaniu typów lista jest w czasie wykonania listą czegokolwiek.
     */
    public String write(List<BattleEvent> events) {
        try {
            return mapper.writerFor(LIST).writeValueAsString(events);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Nie da się zserializować eventów walki", ex);
        }
    }

    public String writeOne(BattleEvent event) {
        try {
            return mapper.writerFor(BattleEvent.class).writeValueAsString(event);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Nie da się zserializować eventu " + event, ex);
        }
    }

    public List<BattleEvent> read(String raw) {
        try {
            return mapper.readValue(raw, LIST);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Nieczytelny zapis eventów", ex);
        }
    }

    public BattleEvent readOne(String raw) {
        try {
            return mapper.readValue(raw, BattleEvent.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Nieczytelny zapis eventu", ex);
        }
    }
}
