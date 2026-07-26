package dev.adamgrochulski.javamon.app.ws;

/**
 * Kody zamknięcia z zakresu 4000–4999, zarezerwowanego dla aplikacji
 * przez RFC 6455. Powód (reason) mieści się w 123 bajtach — dłuższy
 * wywala się przy zamykaniu sesji.
 */
public final class WsClose {

    public static final int UNAUTHORIZED = 4401;
    public static final int AUTH_TIMEOUT = 4408;
    public static final int SESSION_REPLACED = 4409;

    private WsClose() {
    }
}
