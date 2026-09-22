import { ApiError } from './ApiError';
import { session } from './session';
import type {
  AuthResponse, HistoryEntry, LeaderboardEntry, MoveDto,
  SpeciesSummary, TeamRequest, TeamResponse,
} from './types';

type Options = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  /** Publiczne endpointy wołamy bez tokenu, żeby wygasły token ich nie psuł. */
  anonymous?: boolean;
};

async function request<T>(path: string, options: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  const token = session.token();

  if (token && !options.anonymous) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorBody(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

// Błąd potrafi nie mieć ciała (np. 401 z filtra bezpieczeństwa albo padnięty proxy).
async function readErrorBody(response: Response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

export const api = {
  register: (username: string, password: string) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body: { username, password }, anonymous: true }),

  login: (username: string, password: string) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: { username, password }, anonymous: true }),

  guest: () =>
    request<AuthResponse>('/api/auth/guest', { method: 'POST', anonymous: true }),

  logout: () =>
    request<void>('/api/auth/logout', { method: 'POST' }),

  species: () => request<SpeciesSummary[]>('/api/pokemon', { anonymous: true }),

  speciesMoves: (speciesId: string) =>
    request<MoveDto[]>(`/api/pokemon/${speciesId}/moves`, { anonymous: true }),

  teams: () => request<TeamResponse[]>('/api/teams'),

  createTeam: (team: TeamRequest) =>
    request<TeamResponse>('/api/teams', { method: 'POST', body: team }),

  updateTeam: (id: string, team: TeamRequest) =>
    request<TeamResponse>(`/api/teams/${id}`, { method: 'PUT', body: team }),

  deleteTeam: (id: string) =>
    request<void>(`/api/teams/${id}`, { method: 'DELETE' }),

  history: () => request<HistoryEntry[]>('/api/battles/history'),

  leaderboard: () => request<LeaderboardEntry[]>('/api/leaderboard', { anonymous: true }),
};
