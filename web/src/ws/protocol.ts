/**
 * Protokół WebSocket, spisany z docs/protocol.md. Te typy są jedynym miejscem,
 * w którym front wie, co potrafi serwer - reszta aplikacji renderuje wyłącznie
 * z eventów i nigdy niczego nie dolicza sama.
 */

export type Player = 'P1' | 'P2';
export type StatusCondition = 'NONE' | 'BRN' | 'PSN' | 'TOX' | 'PAR' | 'SLP' | 'FRZ';
export type Weather = 'NONE' | 'RAIN' | 'SUN' | 'SANDSTORM' | 'SNOW';
export type Terrain = 'NONE' | 'ELECTRIC' | 'GRASSY' | 'MISTY' | 'PSYCHIC';
export type Stat = 'ATTACK' | 'DEFENSE' | 'SPECIAL_ATTACK' | 'SPECIAL_DEFENSE' | 'SPEED';
export type SideCondition =
  | 'STEALTH_ROCK' | 'SPIKES' | 'TOXIC_SPIKES' | 'STICKY_WEB'
  | 'REFLECT' | 'LIGHT_SCREEN' | 'AURORA_VEIL';

/**
 * Wskazanie Pokémona w evencie. `teamIndex` przeciwnika też przychodzi:
 * każdy event dotyczy kogoś, kto jest albo właśnie wchodzi na pole, więc
 * i tak jest odsłonięty, a front potrzebuje stabilnego klucza.
 */
export type PokemonRef = {
  player: Player;
  teamIndex: number;
  name: string;
};

/**
 * UWAGA: `damage`, `remainingHp` i `amount` to **punkty HP dla twojego**
 * Pokémona, a **procenty maksymalnego HP dla przeciwnika**. Rozstrzyga
 * `player` w `ref` porównany z `you` z BATTLE_START.
 */
export type BattleEvent =
  | { type: 'SWITCH'; out: PokemonRef; in: PokemonRef }
  | { type: 'MOVE_USED'; user: PokemonRef; moveName: string }
  | { type: 'MOVE_MISSED'; user: PokemonRef; moveName: string }
  | { type: 'DAMAGE'; target: PokemonRef; damage: number; remainingHp: number; crit: boolean; effectiveness: number }
  | { type: 'NO_EFFECT'; target: PokemonRef }
  | { type: 'FAINT'; who: PokemonRef }
  | { type: 'STATUS_TICK'; who: PokemonRef; status: StatusCondition; damage: number; remainingHp: number }
  | { type: 'STATUS_INFLICTED'; target: PokemonRef; status: StatusCondition }
  | { type: 'STAT_STAGE_CHANGED'; who: PokemonRef; stat: Stat; delta: number; newStage: number }
  | { type: 'HEALED'; who: PokemonRef; amount: number; remainingHp: number }
  | { type: 'RECOIL_DAMAGE'; who: PokemonRef; damage: number; remainingHp: number }
  | { type: 'HAZARD_SET'; side: Player; condition: SideCondition }
  | { type: 'HAZARD_HURT'; who: PokemonRef; condition: SideCondition; damage: number; remainingHp: number }
  | { type: 'IMMOBILIZED'; who: PokemonRef; status: StatusCondition }
  | { type: 'FLINCHED'; who: PokemonRef }
  | { type: 'CONFUSION_STARTED'; who: PokemonRef }
  | { type: 'CONFUSION_HIT'; who: PokemonRef; damage: number; remainingHp: number }
  | { type: 'CONFUSION_ENDED'; who: PokemonRef }
  | { type: 'CHARGING'; who: PokemonRef; moveName: string }
  | { type: 'RECHARGING'; who: PokemonRef }
  | { type: 'TRAPPED'; who: PokemonRef }
  | { type: 'TRAP_HURT'; who: PokemonRef; damage: number; remainingHp: number }
  | { type: 'TRAP_ENDED'; who: PokemonRef }
  | { type: 'PROTECT_STARTED'; who: PokemonRef }
  | { type: 'PROTECTED'; who: PokemonRef }
  | { type: 'MOVE_FAILED'; who: PokemonRef; moveName: string }
  | { type: 'ONE_HIT_KO'; target: PokemonRef }
  | { type: 'SEEDED'; who: PokemonRef }
  | { type: 'LEECH_SEED_DRAIN'; from: PokemonRef; to: PokemonRef; amount: number }
  | { type: 'WEATHER_STARTED'; weather: Weather }
  | { type: 'WEATHER_HURT'; who: PokemonRef; weather: Weather; damage: number; remainingHp: number }
  | { type: 'WEATHER_ENDED'; weather: Weather }
  | { type: 'SCREEN_SET'; side: Player; condition: SideCondition }
  | { type: 'SCREEN_FADED'; side: Player; condition: SideCondition }
  | { type: 'TERRAIN_STARTED'; terrain: Terrain }
  | { type: 'TERRAIN_ENDED'; terrain: Terrain }
  | { type: 'FORFEIT'; who: Player }
  /** `winner: null` znaczy remis. Brak pola znaczyłby "nie wiem" - to różne rzeczy. */
  | { type: 'BATTLE_END'; winner: Player | null };

export type MoveView = {
  index: number;
  name: string;
  type: string;
  pp: number;
  maxPp: number;
};

export type TeamMember = {
  teamIndex: number;
  speciesId: string;
  name: string;
  level: number;
  maxHp: number;
  currentHp: number;
  status: StatusCondition;
  types: string[];
  moves: MoveView[];
};

/** Przeciwnik bez maxHp i bez movesetu: to jest informacja ukryta. */
export type OpponentActive = {
  teamIndex: number;
  name: string;
  level: number;
  hpPercent: number;
  status: StatusCondition;
  types: string[];
};

/** Podpowiedź do renderowania przycisków, nie autoryzacja - serwer waliduje od zera. */
export type LegalActions = {
  moves: { index: number; name: string; pp: number; usable: boolean; reason: string | null }[];
  switches: number[];
};

export type ServerFrame =
  | { type: 'AUTH_OK'; seq?: number; payload: { trainerId: string; username: string; guest: boolean } }
  | { type: 'QUEUED'; seq?: number; payload: { since: string } }
  | { type: 'QUEUE_LEFT'; seq?: number; payload?: null }
  | {
      type: 'BATTLE_START';
      seq: number;
      payload: {
        battleId: string;
        you: Player;
        opponent: { username: string; rating: number };
        yourTeam: TeamMember[];
        opponentActive: OpponentActive;
      };
    }
  | {
      type: 'REQUEST_ACTION';
      seq: number;
      payload: {
        battleId: string;
        turn: number;
        kind: 'TURN' | 'REPLACEMENT';
        deadline: string;
        legal: LegalActions;
      };
    }
  | { type: 'TURN_EVENTS'; seq: number; payload: { battleId: string; turn: number; events: BattleEvent[] } }
  | {
      type: 'BATTLE_END';
      seq: number;
      payload: {
        battleId: string;
        winner: Player | null;
        reason: 'ko' | 'forfeit' | 'timeout';
        ratingBefore: number | null;
        ratingAfter: number | null;
      };
    }
  | { type: 'ERROR'; payload: { code: WsErrorCode; message: string } }
  | { type: 'PONG'; payload?: null };

export type WsErrorCode =
  | 'bad_frame' | 'unauthenticated' | 'illegal_action' | 'stale_action'
  | 'action_already_submitted' | 'wrong_phase' | 'not_in_battle'
  | 'team_invalid' | 'not_implemented' | 'internal_error';

export type ClientFrame =
  | { type: 'AUTH'; payload: { token: string } }
  | { type: 'QUEUE_JOIN'; payload: { teamId: string } }
  | { type: 'QUEUE_LEAVE' }
  | { type: 'MOVE'; payload: { battleId: string; turn: number; moveIndex: number } }
  | { type: 'SWITCH'; payload: { battleId: string; turn: number; benchIndex: number } }
  | { type: 'FORFEIT'; payload: { battleId: string } }
  | { type: 'RESUME'; payload: { battleId: string; lastSeq: number } }
  | { type: 'PING' };

/** Kody zamknięcia z zakresu aplikacyjnego (RFC 6455 rezerwuje 4000-4999). */
export const WS_CLOSE = {
  UNAUTHORIZED: 4401,
  AUTH_TIMEOUT: 4408,
  SESSION_REPLACED: 4409,
} as const;
