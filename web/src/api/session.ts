const TOKEN_KEY = 'javamon.token';

/**
 * Token w localStorage, nie w ciasteczku. To jedyny powód, dla którego backend
 * ma wyłączone CSRF: nagłówka Authorization przeglądarka sama nie dokleja,
 * więc obca strona nie wykona żądania w naszym imieniu. Przeniesienie tokenu
 * do ciasteczka wymaga włączenia CSRF z powrotem po stronie serwera.
 */
export const session = {
  token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  },

  save(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  },

  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
  },
};
