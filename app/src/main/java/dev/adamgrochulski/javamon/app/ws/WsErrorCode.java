package dev.adamgrochulski.javamon.app.ws;

import com.fasterxml.jackson.annotation.JsonValue;

public enum WsErrorCode {

    BAD_FRAME("bad_frame"),
    UNAUTHENTICATED("unauthenticated"),
    ILLEGAL_ACTION("illegal_action"),
    STALE_ACTION("stale_action"),
    ACTION_ALREADY_SUBMITTED("action_already_submitted"),
    WRONG_PHASE("wrong_phase"),
    NOT_IN_BATTLE("not_in_battle"),
    TEAM_INVALID("team_invalid"),
    NOT_IMPLEMENTED("not_implemented"),
    INTERNAL_ERROR("internal_error");

    private final String wire;

    WsErrorCode(String wire) {
        this.wire = wire;
    }

    /** Postać w JSON-ie. Zmiana nazwy stałej nie może zmienić kontraktu. */
    @JsonValue
    public String wire() {
        return wire;
    }
}
