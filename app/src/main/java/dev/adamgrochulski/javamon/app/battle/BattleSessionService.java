package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.app.persistence.Team;
import dev.adamgrochulski.javamon.app.persistence.TeamSlot;
import dev.adamgrochulski.javamon.app.ws.WsErrorCode;
import dev.adamgrochulski.javamon.app.ws.WsException;
import dev.adamgrochulski.javamon.engine.battle.*;
import dev.adamgrochulski.javamon.engine.damage.TypeChart;
import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.XorShiftRng;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sesje walk i rozliczanie tur. Każda akcja sprawdzana od zera, tak jakby
 * lista legalnych ruchów nigdy nie poszła do klienta.
 */
@Service
public class BattleSessionService {

    // MVP: sesje w pamięci. Redis wchodzi w miejsce tej mapy.
    private final Map<UUID, BattleSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom seedSource = new SecureRandom();

    private final PokemonDex pokemonDex;
    private final MoveDex moveDex;
    private final TypeChart typeChart;

    public BattleSessionService(PokemonDex pokemonDex, MoveDex moveDex, TypeChart typeChart) {
        this.pokemonDex = pokemonDex;
        this.moveDex = moveDex;
        this.typeChart = typeChart;
    }

    /** Skład kopiowany do sesji: usunięcie drużyny w trakcie nie przerywa walki. */
    public BattleSession create(UUID p1TrainerId, Team p1Team, UUID p2TrainerId, Team p2Team) {
        BattleSide side1 = new BattleSide(toBattleTeam(p1Team));
        BattleSide side2 = new BattleSide(toBattleTeam(p2Team));

        // SecureRandom, nie nanoTime: czas startu walki jest odgadywalny.
        Battle battle = new Battle(side1, side2, new XorShiftRng(seedSource.nextLong()), typeChart);

        BattleSession session = new BattleSession(UUID.randomUUID(), battle, p1TrainerId, p2TrainerId);
        sessions.put(session.id(), session);
        return session;
    }

    public BattleSession require(UUID battleId, UUID trainerId) {
        BattleSession session = sessions.get(battleId);
        // Ten sam błąd na "nie ma walki" i "nie twoja walka" - inaczej da się je enumerować.
        if (session == null || session.playerOf(trainerId) == null) {
            throw new WsException(WsErrorCode.NOT_IN_BATTLE, "Nie jesteś uczestnikiem tej walki");
        }
        return session;
    }

    /** Eventy tury, gdy przyszły obie akcje; pusty Optional, gdy czekamy na przeciwnika. */
    public Optional<List<BattleEvent>> submit(UUID battleId, UUID trainerId, int turn, Action action) {
        BattleSession session = require(battleId, trainerId);
        Player player = session.playerOf(trainerId);

        // Blokada na sesji, nie na serwisie: różne walki nie mają powodu czekać na siebie.
        synchronized (session) {
            Battle battle = session.battle();

            if (turn != battle.getTurn()) {
                throw new WsException(WsErrorCode.STALE_ACTION,
                        "Akcja na turę " + turn + ", trwa tura " + battle.getTurn());
            }

            List<Player> awaiting = battle.awaitingReplacement();
            if (!awaiting.isEmpty()) {
                return Optional.of(replace(battle, player, awaiting, action));
            }

            if (session.hasSubmitted(player)) {
                throw new WsException(WsErrorCode.ACTION_ALREADY_SUBMITTED,
                        "Akcja na tę turę już przyszła");
            }
            validate(battle, player, action);

            session.submit(player, action);
            if (!session.bothSubmitted()) {
                return Optional.empty();
            }

            Map<Player, Action> actions = session.takePending();
            return Optional.of(TurnResolver.resolve(battle, actions.get(Player.P1), actions.get(Player.P2)));
        }
    }

    /** Legalność względem stanu. Guardy silnika to druga linia, rzucają w trakcie mutacji. */
    private void validate(Battle battle, Player player, Action action) {
        BattlePokemon active = battle.side(player).active();

        switch (action) {
            case ForfeitAction ignored -> { }

            case MoveAction(int moveIndex) -> {
                if (moveIndex < 0 || moveIndex >= active.moveCount()) {
                    throw new WsException(WsErrorCode.ILLEGAL_ACTION, "Ruch spoza movesetu");
                }
                if (active.ppLeft(moveIndex) <= 0) {
                    throw new WsException(WsErrorCode.ILLEGAL_ACTION, "Ten ruch nie ma PP");
                }
            }

            case SwitchAction(int benchIndex) -> {
                List<BattlePokemon> team = battle.side(player).getTeam();
                if (benchIndex < 0 || benchIndex >= team.size()) {
                    throw new WsException(WsErrorCode.ILLEGAL_ACTION, "Nie ma takiego slotu");
                }
                if (benchIndex == battle.side(player).getActiveIndex()) {
                    throw new WsException(WsErrorCode.ILLEGAL_ACTION, "Ten Pokémon już jest aktywny");
                }
                if (team.get(benchIndex).isFainted()) {
                    throw new WsException(WsErrorCode.ILLEGAL_ACTION, "Ten Pokémon jest padnięty");
                }
                if (active.isTrapped()) {
                    throw new WsException(WsErrorCode.ILLEGAL_ACTION, "Pokémon jest uwięziony");
                }
            }
        }
    }

    /** Po faincie dozwolony jest wyłącznie SWITCH i tylko od gracza, który stracił Pokémona. */
    private List<BattleEvent> replace(Battle battle, Player player, List<Player> awaiting, Action action) {
        if (!awaiting.contains(player)) {
            throw new WsException(WsErrorCode.WRONG_PHASE, "Czekamy na zejście przeciwnika");
        }
        if (!(action instanceof SwitchAction switchAction)) {
            throw new WsException(WsErrorCode.WRONG_PHASE, "Po faincie dozwolony jest tylko SWITCH");
        }
        validateReplacement(battle, player, switchAction);
        return TurnResolver.resolveReplacement(battle, player, switchAction);
    }

    // Bez sprawdzania uwięzienia: aktywny jest martwy, a flaga trapu wciąż na nim wisi.
    private void validateReplacement(Battle battle, Player player, SwitchAction action) {
        List<BattlePokemon> team = battle.side(player).getTeam();
        int index = action.benchIndex();

        if (index < 0 || index >= team.size()) {
            throw new WsException(WsErrorCode.ILLEGAL_ACTION, "Nie ma takiego slotu");
        }
        if (index == battle.side(player).getActiveIndex()) {
            throw new WsException(WsErrorCode.ILLEGAL_ACTION, "Ten Pokémon właśnie padł");
        }
        if (team.get(index).isFainted()) {
            throw new WsException(WsErrorCode.ILLEGAL_ACTION, "Ten Pokémon jest padnięty");
        }
    }

    private List<BattlePokemon> toBattleTeam(Team team) {
        return team.getSlots().stream()
                .sorted(Comparator.comparingInt(TeamSlot::getSlotIndex))
                .map(this::toBattlePokemon)
                .toList();
    }

    private BattlePokemon toBattlePokemon(TeamSlot slot) {
        Species species = pokemonDex.get(slot.getSpeciesId());
        List<Move> moves = slot.getMoves().stream().map(moveDex::get).toList();

        return new BattlePokemon(
                species.name(),
                species.base(),
                species.primary(),
                species.secondary(),
                slot.getLevel(),
                moves);
    }
}
