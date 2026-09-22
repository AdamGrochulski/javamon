package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.app.persistence.Team;
import dev.adamgrochulski.javamon.app.persistence.TeamSlot;
import dev.adamgrochulski.javamon.app.ws.WsErrorCode;
import dev.adamgrochulski.javamon.app.ws.WsException;
import dev.adamgrochulski.javamon.engine.battle.*;
import dev.adamgrochulski.javamon.engine.damage.TypeChart;
import dev.adamgrochulski.javamon.engine.model.BattlePokemon;
import dev.adamgrochulski.javamon.engine.model.MoveDex;
import dev.adamgrochulski.javamon.engine.model.PokemonDex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BattleSessionServiceTest {

    // Dexy ładują ~1.5 MB JSON-a, więc raz na klasę testową, nie raz na test.
    private static final PokemonDex POKEDEX = new PokemonDex();
    private static final MoveDex MOVES = new MoveDex();
    private static final TypeChart CHART = new TypeChart();

    private final BattleSessionService service = new BattleSessionService(POKEDEX, MOVES, CHART);

    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();

    private BattleSession session;

    @BeforeEach
    void setUp() {
        session = service.create(
                new Participant(p1, "ash", 1000),
                team(List.of("charizard", "blastoise", "venusaur", "pikachu", "gengar", "alakazam")),
                new Participant(p2, "gary", 1042),
                team(List.of("snorlax", "lapras", "machamp", "golem", "jolteon", "dragonite")));
    }

    @Test
    void obcy_trener_nie_widzi_walki() {
        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), UUID.randomUUID(), 1, new MoveAction(0)));

        assertEquals(WsErrorCode.NOT_IN_BATTLE, ex.code());
    }

    @Test
    void nieistniejaca_walka_daje_ten_sam_blad_co_cudza() {
        WsException ex = assertThrows(WsException.class,
                () -> service.submit(UUID.randomUUID(), p1, 1, new MoveAction(0)));

        assertEquals(WsErrorCode.NOT_IN_BATTLE, ex.code());
    }

    @Test
    void akcja_na_inna_ture_odrzucona() {
        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), p1, 2, new MoveAction(0)));

        assertEquals(WsErrorCode.STALE_ACTION, ex.code());
    }

    @Test
    void druga_akcja_na_te_sama_ture_odrzucona() {
        service.submit(session.id(), p1, 1, new MoveAction(0));

        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), p1, 1, new MoveAction(1)));

        assertEquals(WsErrorCode.ACTION_ALREADY_SUBMITTED, ex.code());
    }

    @Test
    void ruch_spoza_movesetu_odrzucony() {
        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), p1, 1, new MoveAction(9)));

        assertEquals(WsErrorCode.ILLEGAL_ACTION, ex.code());
    }

    @Test
    void ruch_bez_pp_odrzucony() {
        BattlePokemon active = session.battle().side(Player.P1).active();
        while (active.ppLeft(0) > 0) {
            active.useMove(0);
        }

        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), p1, 1, new MoveAction(0)));

        assertEquals(WsErrorCode.ILLEGAL_ACTION, ex.code());
    }

    @Test
    void switch_na_aktywnego_odrzucony() {
        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), p1, 1, new SwitchAction(0)));

        assertEquals(WsErrorCode.ILLEGAL_ACTION, ex.code());
    }

    @Test
    void switch_na_padnietego_odrzucony() {
        BattlePokemon bench = session.battle().side(Player.P1).getTeam().get(1);
        bench.takeDamage(bench.getMaxHp());

        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), p1, 1, new SwitchAction(1)));

        assertEquals(WsErrorCode.ILLEGAL_ACTION, ex.code());
    }

    @Test
    void switch_poza_zakresem_odrzucony() {
        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), p1, 1, new SwitchAction(6)));

        assertEquals(WsErrorCode.ILLEGAL_ACTION, ex.code());
    }

    @Test
    void tura_rozlicza_sie_dopiero_po_obu_akcjach() {
        assertTrue(service.submit(session.id(), p1, 1, new MoveAction(0)).isEmpty());

        Optional<List<BattleEvent>> events = service.submit(session.id(), p2, 1, new MoveAction(0));

        assertTrue(events.isPresent());
        assertFalse(events.get().isEmpty());
        assertEquals(2, session.battle().getTurn());
    }

    @Test
    void odrzucona_akcja_nie_blokuje_poprawnej() {
        assertThrows(WsException.class,
                () -> service.submit(session.id(), p1, 1, new MoveAction(9)));

        assertTrue(service.submit(session.id(), p1, 1, new MoveAction(0)).isEmpty());
    }

    @Test
    void po_faincie_ruch_odrzucony() {
        killActive(Player.P1);

        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), p1, 1, new MoveAction(0)));

        assertEquals(WsErrorCode.WRONG_PHASE, ex.code());
    }

    @Test
    void przeciwnik_nie_gra_dopoki_zejscie_nie_wybrane() {
        killActive(Player.P1);

        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), p2, 1, new MoveAction(0)));

        assertEquals(WsErrorCode.WRONG_PHASE, ex.code());
    }

    @Test
    void zejscie_po_faincie_zmienia_aktywnego() {
        killActive(Player.P1);

        Optional<List<BattleEvent>> events = service.submit(session.id(), p1, 1, new SwitchAction(1));

        assertTrue(events.isPresent());
        assertEquals(1, session.battle().side(Player.P1).getActiveIndex());
        assertTrue(session.battle().awaitingReplacement().isEmpty());
    }

    @Test
    void zejscie_na_padnietego_odrzucone_takze_po_faincie() {
        killActive(Player.P1);
        BattlePokemon bench = session.battle().side(Player.P1).getTeam().get(1);
        bench.takeDamage(bench.getMaxHp());

        WsException ex = assertThrows(WsException.class,
                () -> service.submit(session.id(), p1, 1, new SwitchAction(1)));

        assertEquals(WsErrorCode.ILLEGAL_ACTION, ex.code());
    }

    @Test
    void legal_oznacza_ruch_bez_pp_jako_niedostepny() {
        BattlePokemon active = session.battle().side(Player.P1).active();
        while (active.ppLeft(0) > 0) {
            active.useMove(0);
        }

        LegalActions.LegalMove move = service.legalActions(session, Player.P1).moves().get(0);

        assertFalse(move.usable());
        assertEquals("Brak PP", move.reason());
    }

    @Test
    void legal_po_faincie_to_same_zejscia() {
        killActive(Player.P1);

        LegalActions legal = service.legalActions(session, Player.P1);

        assertTrue(legal.moves().isEmpty());
        assertEquals(List.of(1, 2, 3, 4, 5), legal.switches());
    }

    @Test
    void uwieziony_nie_ma_zejsc() {
        session.battle().side(Player.P1).active().trap(3);

        LegalActions legal = service.legalActions(session, Player.P1);

        assertTrue(legal.switches().isEmpty());
        assertFalse(legal.moves().isEmpty());
    }

    @Test
    void poddanie_konczy_walke() {
        Optional<List<BattleEvent>> events = service.submit(session.id(), p1, 1, new ForfeitAction());

        assertTrue(events.isPresent());
        assertEquals(new BattleEvent.BattleEnd(Player.P2), events.get().get(1));
    }

    @Test
    void poddac_sie_mozna_takze_po_faincie() {
        killActive(Player.P1);

        Optional<List<BattleEvent>> events = service.submit(session.id(), p1, 1, new ForfeitAction());

        assertTrue(events.isPresent());
        assertEquals(new BattleEvent.BattleEnd(Player.P2), events.get().get(1));
    }

    private void killActive(Player player) {
        BattlePokemon active = session.battle().side(player).active();
        active.takeDamage(active.getMaxHp());
    }

    private Team team(List<String> speciesIds) {
        Team team = new Team(null, "test");
        List<TeamSlot> slots = new ArrayList<>();
        for (int i = 0; i < speciesIds.size(); i++) {
            slots.add(new TeamSlot(i, speciesIds.get(i), 50, movesOf(speciesIds.get(i))));
        }
        team.addSlots(slots);
        return team;
    }

    /** Cztery ruchy z learnsetu, posortowane - learnset jest Setem, kolejność nie jest ustalona. */
    private List<String> movesOf(String speciesId) {
        return POKEDEX.get(speciesId).learnset().stream().sorted().limit(4).toList();
    }
}
