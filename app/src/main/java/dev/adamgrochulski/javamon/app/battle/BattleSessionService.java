package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.app.persistence.BattleResult;
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
import java.time.Instant;
import java.util.*;

/**
 * Sesje walk i rozliczanie tur. Każda akcja sprawdzana od zera, tak jakby
 * lista legalnych ruchów nigdy nie poszła do klienta.
 * <p>
 * Źródłem prawdy jest magazyn, nie pamięć: każda zmiana to wczytaj, zastosuj, zapisz,
 * a wszystko to pod zamkiem tej jednej walki.
 */
@Service
public class BattleSessionService {

    private final BattleStore store;
    private final BattleArchive archive;
    private final PokemonDex pokemonDex;
    private final MoveDex moveDex;
    private final TypeChart typeChart;
    private final SecureRandom seedSource = new SecureRandom();

    public BattleSessionService(BattleStore store, BattleArchive archive,
                                PokemonDex pokemonDex, MoveDex moveDex, TypeChart typeChart) {
        this.store = store;
        this.archive = archive;
        this.pokemonDex = pokemonDex;
        this.moveDex = moveDex;
        this.typeChart = typeChart;
    }

    /** Skład kopiowany do sesji: usunięcie drużyny w trakcie nie przerywa walki. */
    public BattleSession create(Participant p1, Team p1Team, Participant p2, Team p2Team) {
        return create(p1, rosterOf(p1Team), p2, rosterOf(p2Team));
    }

    public BattleSession create(Participant p1, List<MonSnapshot> roster1,
                                Participant p2, List<MonSnapshot> roster2) {
        // SecureRandom, nie nanoTime: czas startu walki jest odgadywalny.
        XorShiftRng rng = new XorShiftRng(seedSource.nextLong());
        Battle battle = new Battle(sideOf(roster1), sideOf(roster2), rng, typeChart);

        BattleSession session = new BattleSession(
                UUID.randomUUID(), battle, rng, p1, p2, roster1, roster2, Instant.now());
        store.save(session.toSnapshot());
        return session;
    }

    public BattleSession require(UUID battleId, UUID trainerId) {
        BattleSession session = store.load(battleId).map(this::fromSnapshot).orElse(null);
        // Ten sam błąd na "nie ma walki" i "nie twoja walka" - inaczej da się je enumerować.
        if (session == null || session.playerOf(trainerId) == null) {
            throw new WsException(WsErrorCode.NOT_IN_BATTLE, "Nie jesteś uczestnikiem tej walki");
        }
        return session;
    }

    /** Co gracz może teraz zrobić. Liczone dla obu faz: normalnej tury i zejścia po faincie. */
    public LegalActions legalActions(BattleSession session, Player player) {
        Battle battle = session.battle();
        BattleSide side = battle.side(player);

        List<Integer> switches = new ArrayList<>();
        for (int i = 0; i < side.getTeam().size(); i++) {
            if (i != side.getActiveIndex() && !side.getTeam().get(i).isFainted()) {
                switches.add(i);
            }
        }

        if (battle.needsReplacement(player)) {
            return new LegalActions(List.of(), switches);
        }

        BattlePokemon active = side.active();
        List<LegalActions.LegalMove> moves = new ArrayList<>();
        for (int i = 0; i < active.moveCount(); i++) {
            int pp = active.ppLeft(i);
            moves.add(new LegalActions.LegalMove(
                    i, active.moveAt(i).name(), pp, pp > 0, pp > 0 ? null : "Brak PP"));
        }

        // Uwięziony nie zejdzie. Po faincie trap nie obowiązuje, ale tamta gałąź wyszła wyżej.
        return new LegalActions(moves, active.isTrapped() ? List.of() : switches);
    }

    /** Eventy tury, gdy przyszły obie akcje; pusty Optional, gdy czekamy na przeciwnika. */
    public Optional<TurnOutcome> submit(UUID battleId, UUID trainerId, int turn, Action action) {
        return store.locked(battleId, () -> {
            BattleSession session = require(battleId, trainerId);
            Player player = session.playerOf(trainerId);
            Battle battle = session.battle();

            if (session.isFinished()) {
                throw new WsException(WsErrorCode.WRONG_PHASE, "Ta walka jest już zakończona");
            }
            if (turn != battle.getTurn()) {
                throw new WsException(WsErrorCode.STALE_ACTION,
                        "Akcja na turę " + turn + ", trwa tura " + battle.getTurn());
            }

            // Poddać się wolno zawsze, także gdy wisi zejście po faincie.
            if (action instanceof ForfeitAction) {
                session.takePending();
                return persist(session, turn, TurnResolver.resolveForfeit(battle, player),
                        BattleResult.FORFEIT);
            }

            List<Player> awaiting = battle.awaitingReplacement();
            if (!awaiting.isEmpty()) {
                return persist(session, turn, replace(battle, player, awaiting, action), BattleResult.KO);
            }

            if (session.hasSubmitted(player)) {
                throw new WsException(WsErrorCode.ACTION_ALREADY_SUBMITTED,
                        "Akcja na tę turę już przyszła");
            }
            validate(battle, player, action);
            session.submit(player, action);

            if (!session.bothSubmitted()) {
                store.save(session.toSnapshot());
                return Optional.empty();
            }

            Map<Player, Action> actions = session.takePending();
            return persist(session, turn,
                    TurnResolver.resolve(battle, actions.get(Player.P1), actions.get(Player.P2)),
                    BattleResult.KO);
        });
    }

    /** Poddanie bez numeru tury: wolno je zgłosić w każdej fazie, także między turami. */
    public Optional<TurnOutcome> forfeit(UUID battleId, UUID trainerId) {
        return store.locked(battleId, () -> {
            BattleSession session = require(battleId, trainerId);
            if (session.isFinished()) {
                throw new WsException(WsErrorCode.WRONG_PHASE, "Ta walka jest już zakończona");
            }
            int turn = session.battle().getTurn();
            session.takePending();
            return persist(session, turn,
                    TurnResolver.resolveForfeit(session.battle(), session.playerOf(trainerId)),
                    BattleResult.FORFEIT);
        });
    }

    /**
     * Upłynął czas na akcję. Pusty Optional oznacza, że timer się spóźnił i nie ma nic do zrobienia:
     * tura zdążyła się rozliczyć albo obaj gracze zdążyli przysłać akcje.
     */
    public Optional<TurnOutcome> timeout(UUID battleId, int turn) {
        return store.locked(battleId, () -> {
            BattleSession session = store.load(battleId).map(this::fromSnapshot).orElse(null);
            if (session == null || session.isFinished() || session.battle().getTurn() != turn) {
                return Optional.empty();
            }

            Battle battle = session.battle();
            List<Player> awaiting = battle.awaitingReplacement();
            List<Player> asked = awaiting.isEmpty() ? List.of(Player.P1, Player.P2) : awaiting;
            List<Player> silent = asked.stream().filter(player -> !session.hasSubmitted(player)).toList();
            if (silent.isEmpty()) {
                return Optional.empty();
            }

            session.takePending();
            return persist(session, turn, TurnResolver.resolveTimeout(battle, silent), BattleResult.TIMEOUT);
        });
    }

    private Optional<TurnOutcome> persist(BattleSession session, int turn,
                                          List<BattleEvent> events, BattleResult result) {
        store.appendEvents(session.id(), events);

        BattleEvent.BattleEnd end = events.stream()
                .filter(BattleEvent.BattleEnd.class::isInstance)
                .map(BattleEvent.BattleEnd.class::cast)
                .findFirst()
                .orElse(null);

        if (end == null) {
            store.save(session.toSnapshot());
            return Optional.of(new TurnOutcome(session, turn, events, null));
        }

        session.finish();
        store.save(session.toSnapshot());
        // Archiwum dostaje całą historię, nie tylko ostatnią turę - replay to cała walka.
        BattleSummary summary = archive.archive(session, result, end.winner(), store.events(session.id()));
        return Optional.of(new TurnOutcome(session, turn, events, summary));
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

    private BattleSession fromSnapshot(BattleSnapshot snapshot) {
        XorShiftRng rng = new XorShiftRng(snapshot.rngState());
        Battle battle = new Battle(sideOf(snapshot.p1Team()), sideOf(snapshot.p2Team()), rng, typeChart);
        battle.restore(snapshot.state());

        BattleSession session = new BattleSession(snapshot.id(), battle, rng,
                snapshot.p1(), snapshot.p2(), snapshot.p1Team(), snapshot.p2Team(), snapshot.startedAt());
        session.restorePending(snapshot.pending(), snapshot.finished());
        return session;
    }

    public List<MonSnapshot> rosterOf(Team team) {
        return team.getSlots().stream()
                .sorted(Comparator.comparingInt(TeamSlot::getSlotIndex))
                .map(slot -> new MonSnapshot(slot.getSpeciesId(), slot.getLevel(), slot.getMoves()))
                .toList();
    }

    private BattleSide sideOf(List<MonSnapshot> roster) {
        return new BattleSide(roster.stream().map(this::toBattlePokemon).toList());
    }

    private BattlePokemon toBattlePokemon(MonSnapshot mon) {
        Species species = pokemonDex.get(mon.speciesId());
        List<Move> moves = mon.moves().stream().map(moveDex::get).toList();

        return new BattlePokemon(
                species.name(),
                species.base(),
                species.primary(),
                species.secondary(),
                mon.level(),
                moves);
    }
}
