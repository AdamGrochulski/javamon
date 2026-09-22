import type { ClientFrame, ServerFrame } from './protocol';
import { WS_CLOSE } from './protocol';

type Listener = (frame: ServerFrame) => void;
type CloseListener = (code: number, reason: string) => void;

/**
 * Połączenie z serwerem walki. Zna tylko transport i uwierzytelnienie:
 * co zrobić z ramką, decyduje warstwa wyżej.
 */
export class BattleSocket {
  private socket: WebSocket | null = null;
  private readonly listeners = new Set<Listener>();
  private readonly closeListeners = new Set<CloseListener>();

  /** Numer ostatniej odebranej ramki tej walki - z nim wraca się po zerwaniu. */
  private lastSeq = 0;
  private battleId: string | null = null;

  constructor(private readonly url = wsUrl()) {}

  /**
   * Łączy i przedstawia się. Token idzie pierwszą ramką, a nie w adresie:
   * URL z tokenem trafia do logów proxy i do historii przeglądarki.
   */
  connect(token: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(this.url);
      this.socket = socket;

      socket.onopen = () => socket.send(JSON.stringify({ type: 'AUTH', payload: { token } }));

      socket.onmessage = (message) => {
        const frame = JSON.parse(message.data as string) as ServerFrame;

        if (frame.type === 'AUTH_OK') {
          resolve();
        }
        if (frame.type === 'ERROR' && frame.payload.code === 'unauthenticated') {
          reject(new Error(frame.payload.message));
        }
        this.remember(frame);
        this.listeners.forEach((listener) => listener(frame));
      };

      socket.onclose = (event) => {
        this.socket = null;
        // 4401/4408/4409 dotyczą tożsamości: ponowne łączenie nic nie da.
        reject(new Error(closeReason(event.code)));
        this.closeListeners.forEach((listener) => listener(event.code, event.reason));
      };

      socket.onerror = () => reject(new Error('Nie udało się połączyć z serwerem'));
    });
  }

  send(frame: ClientFrame): void {
    if (this.socket?.readyState !== WebSocket.OPEN) {
      throw new Error('Połączenie jest zamknięte');
    }
    this.socket.send(JSON.stringify(frame));
  }

  /** Dosyłka po zerwaniu. Serwer sam zdecyduje, czy starczy przyrost, czy potrzebny pełny stan. */
  resume(): void {
    if (this.battleId) {
      this.send({ type: 'RESUME', payload: { battleId: this.battleId, lastSeq: this.lastSeq } });
    }
  }

  onFrame(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  onClose(listener: CloseListener): () => void {
    this.closeListeners.add(listener);
    return () => this.closeListeners.delete(listener);
  }

  close(): void {
    this.socket?.close(1000, 'Koniec');
    this.socket = null;
  }

  private remember(frame: ServerFrame): void {
    if ('seq' in frame && typeof frame.seq === 'number') {
      this.lastSeq = frame.seq;
    }
    if (frame.type === 'BATTLE_START') {
      this.battleId = frame.payload.battleId;
      // Nowa walka, nowa numeracja - inaczej RESUME prosiłby o ramki z poprzedniej.
      this.lastSeq = frame.seq;
    }
    if (frame.type === 'BATTLE_END') {
      this.battleId = null;
      this.lastSeq = 0;
    }
  }
}

function wsUrl(): string {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${location.host}/ws/battle`;
}

function closeReason(code: number): string {
  switch (code) {
    case WS_CLOSE.UNAUTHORIZED:
      return 'Token odrzucony, zaloguj się ponownie';
    case WS_CLOSE.AUTH_TIMEOUT:
      return 'Serwer nie doczekał się uwierzytelnienia';
    case WS_CLOSE.SESSION_REPLACED:
      return 'To konto połączyło się w innej karcie';
    default:
      return 'Połączenie zostało zamknięte';
  }
}
