package dev.adamgrochulski.javamon.app.ws;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import org.springframework.stereotype.Component;

/**
 * Serializacja protokołu. Trzyma własny ObjectMapper — kopię tego z MVC.
 * <p>
 * Kopia, nie ten sam obiekt: mixin i ustawienia protokołu nie mają prawa
 * zmieniać odpowiedzi REST-a. Osobny bean typu ObjectMapper też odpada —
 * autokonfiguracja Jacksona ma @ConditionalOnMissingBean(ObjectMapper.class),
 * więc własny bean tego typu wyłączyłby ją dla całego MVC.
 */
@Component
public class WsJson {
    private final ObjectMapper mapper;
    WsJson(ObjectMapper springMapper) {
        this.mapper = springMapper.copy();
        this.mapper.addMixIn(BattleEvent.class, BattleEventMixin.class);
    }

    public ClientFrame read(String raw) {
        ClientFrame frame;
        try {
            frame = mapper.readValue(raw, ClientFrame.class);
        } catch (JacksonException ex) {
            throw new WsException(WsErrorCode.BAD_FRAME, "Ramka nie jest poprawnym JSON-em");
        }
        if(frame == null || frame.type() == null || frame.type().isBlank()) {
            throw new WsException(WsErrorCode.BAD_FRAME, "Ramka bez pola type");
        }
        return frame;
    }

    public <T> T payload(ClientFrame frame, Class<T> type) {
        if (frame.payload() == null || frame.payload().isNull()) {
            throw new WsException(WsErrorCode.BAD_FRAME,
                    "Ramka " + frame.type() + " wymaga payloadu");
        }
        try {
            return mapper.treeToValue(frame.payload(), type);
        } catch (JacksonException ex) {
            throw new WsException(WsErrorCode.BAD_FRAME,
                    "Payload ramki " + frame.type() + " ma zły kształt");
        }
    }

    public String write(ServerFrame frame) {
        try {
            return mapper.writeValueAsString(frame);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Nie da się zserializować ramki " + frame.type(), ex);
        }
    }
}
