package dev.adamgrochulski.javamon.app.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Ramka przychodząca. Payload zostaje surowym drzewem JSON, bo jego kształt
 * zależy od pola {@code type} — poznajemy go dopiero po dyspozycji.
 * <p>
 * ignoreUnknown = true: nowszy klient może dokleić pole, którego jeszcze nie
 * znamy. Zerwanie połączenia z tego powodu byłoby nieproporcjonalne.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientFrame(String type, JsonNode payload) {
}
