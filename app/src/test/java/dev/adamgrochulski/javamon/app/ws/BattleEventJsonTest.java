package dev.adamgrochulski.javamon.app.ws;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.adamgrochulski.javamon.engine.battle.BattleEvent;
import dev.adamgrochulski.javamon.engine.battle.Player;
import dev.adamgrochulski.javamon.engine.model.Weather;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bez Springa — sprawdzamy samą serializację protokołu.
 */
class BattleEventJsonTest {

    private final ObjectMapper plain = new ObjectMapper();
    private final WsJson json = new WsJson(new ObjectMapper());

    private static JsonSubTypes.Type[] mixinTypes() {
        return BattleEventMixin.class.getAnnotation(JsonSubTypes.class).value();
    }

    /**
     * BattleEvent jest sealed, więc JVM zna pełną listę wariantów. Dodanie
     * eventu w silniku bez wpisu w mixinie kończy się tutaj, a nie ciszą
     * na produkcji.
     */
    @Test
    void kazdyWariantBattleEventMaNazweWProtokole() {
        Set<Class<?>> permitted = Set.of(BattleEvent.class.getPermittedSubclasses());
        Set<Class<?>> mapped = Arrays.stream(mixinTypes())
                .map(JsonSubTypes.Type::value)
                .collect(Collectors.toSet());

        assertThat(mapped).containsExactlyInAnyOrderElementsOf(permitted);
    }

    @Test
    void nazwyNaDrucieSaUnikalneIWKonwencjiProtokolu() {
        List<String> names = Arrays.stream(mixinTypes()).map(JsonSubTypes.Type::name).toList();

        assertThat(names).doesNotHaveDuplicates();
        assertThat(names).allMatch(name -> name.matches("[A-Z][A-Z0-9_]*"));
    }

    @Test
    void damageSerializujeSieZgodnieZDokumentacja() throws Exception {
        BattleEvent damage = new BattleEvent.Damage(
                new BattleEvent.PokemonRef(Player.P2, 0, "Blastoise"), 41, 132, false, 0.5);

        JsonNode event = firstEventOf(damage);

        assertThat(event.get("type").asText()).isEqualTo("DAMAGE");
        assertThat(event.get("target").get("player").asText()).isEqualTo("P2");
        assertThat(event.get("target").get("teamIndex").asInt()).isZero();
        assertThat(event.get("target").get("name").asText()).isEqualTo("Blastoise");
        assertThat(event.get("damage").asInt()).isEqualTo(41);
        assertThat(event.get("remainingHp").asInt()).isEqualTo(132);
        assertThat(event.get("crit").asBoolean()).isFalse();
        assertThat(event.get("effectiveness").asDouble()).isEqualTo(0.5);
    }

    /** Brak pola znaczy "nie wiem", null znaczy "remis". To nie to samo. */
    @Test
    void battleEndZachowujeNullowegoZwyciezce() throws Exception {
        JsonNode event = firstEventOf(new BattleEvent.BattleEnd(null));

        assertThat(event.has("winner")).isTrue();
        assertThat(event.get("winner").isNull()).isTrue();
    }

    @Test
    void enumyIdaJakoStringiZSilnika() throws Exception {
        JsonNode event = firstEventOf(new BattleEvent.WeatherStarted(Weather.SANDSTORM));

        assertThat(event.get("weather").asText()).isEqualTo("SANDSTORM");
    }

    @Test
    void kopertaPomijaSeqTamGdzieGoNieMa() throws Exception {
        JsonNode pong = plain.readTree(json.write(new ServerFrame("PONG", null, null)));

        assertThat(pong.get("type").asText()).isEqualTo("PONG");
        assertThat(pong.has("seq")).isFalse();
        assertThat(pong.has("payload")).isFalse();
    }

    private JsonNode firstEventOf(BattleEvent event) throws Exception {
        String raw = json.write(new ServerFrame("TURN_EVENTS", 3L,
                new WsDtos.TurnEvents(UUID.randomUUID(), 1, List.of(event))));

        return plain.readTree(raw).get("payload").get("events").get(0);
    }
}
