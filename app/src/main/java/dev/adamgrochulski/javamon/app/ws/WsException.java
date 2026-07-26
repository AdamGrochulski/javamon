package dev.adamgrochulski.javamon.app.ws;

/** Błąd protokołu, na który odpowiadamy ramką ERROR — bez zamykania sesji. */
public class WsException extends RuntimeException {

    private final WsErrorCode code;

    public WsException(WsErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public WsErrorCode code() {
        return code;
    }
}
