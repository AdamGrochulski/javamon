package dev.adamgrochulski.javamon.app.battle;

import dev.adamgrochulski.javamon.engine.battle.Action;
import dev.adamgrochulski.javamon.engine.battle.ForfeitAction;
import dev.adamgrochulski.javamon.engine.battle.MoveAction;
import dev.adamgrochulski.javamon.engine.battle.SwitchAction;

/** Akcja w postaci nadającej się do zapisu. Sealed Action nie ma kształtu dla JSON-a. */
public record ActionSnapshot(Kind kind, int index) {

    public enum Kind { MOVE, SWITCH, FORFEIT }

    public static ActionSnapshot of(Action action) {
        return switch (action) {
            case MoveAction(int moveIndex) -> new ActionSnapshot(Kind.MOVE, moveIndex);
            case SwitchAction(int benchIndex) -> new ActionSnapshot(Kind.SWITCH, benchIndex);
            case ForfeitAction ignored -> new ActionSnapshot(Kind.FORFEIT, 0);
        };
    }

    public Action toAction() {
        return switch (kind) {
            case MOVE -> new MoveAction(index);
            case SWITCH -> new SwitchAction(index);
            case FORFEIT -> new ForfeitAction();
        };
    }
}
