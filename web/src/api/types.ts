/**
 * Kształt odpowiedzi REST-a. Ręcznie spisany z kontraktu, nie generowany:
 * generator dokładałby krok do builda, a tych typów jest kilkanaście i zmieniają
 * się rzadko. Rozjazd z backendem wyłapie pierwszy ekran, który ich użyje.
 */

export type AuthResponse = {
  token: string;
  username: string;
  guest: boolean;
};

export type StatsDto = {
  hp: number;
  attack: number;
  defense: number;
  specialAttack: number;
  specialDefense: number;
  speed: number;
};

export type SpeciesSummary = {
  id: string;
  name: string;
  num: number;
  primaryType: string;
  /** null dla jednotypowych */
  secondaryType: string | null;
  base: StatsDto;
};

export type MoveDto = {
  name: string;
  type: string;
  category: 'PHYSICAL' | 'SPECIAL' | 'STATUS';
  power: number;
  accuracy: number;
  pp: number;
  priority: number;
  /** Ruch ładuje się z podstawą, ale jego unikatowa mechanika nie jest jeszcze modelowana. */
  simplified: boolean;
};

export type SlotRequest = {
  speciesId: string;
  level: number;
  moves: string[];
};

export type TeamRequest = {
  name: string;
  slots: SlotRequest[];
};

export type SlotResponse = SlotRequest & { slotIndex: number };

export type TeamResponse = {
  id: string;
  name: string;
  slots: SlotResponse[];
};

export type BattleResult = 'KO' | 'FORFEIT' | 'TIMEOUT';

export type HistoryEntry = {
  battleId: string;
  opponent: string;
  won: boolean;
  draw: boolean;
  result: BattleResult;
  turns: number;
  ratingBefore: number;
  ratingAfter: number;
  finishedAt: string;
};

export type LeaderboardEntry = {
  rank: number;
  username: string;
  rating: number;
};

/** Jednolite ciało błędu z backendu: pole `fields` tylko przy błędach walidacji. */
export type ApiErrorBody = {
  error: string;
  message: string;
  fields?: Record<string, string>;
};
