import type { ApiErrorBody } from './types';

/**
 * Błąd z kodem, nie samym tekstem. Komunikat z backendu jest po polsku i nadaje
 * się do pokazania, ale decyzje (np. "podświetl pole nick") podejmujemy po `code`.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: Record<string, string>;

  constructor(status: number, body: ApiErrorBody | null) {
    super(body?.message ?? 'Coś poszło nie tak');
    this.status = status;
    this.code = body?.error ?? 'unknown';
    this.fields = body?.fields ?? {};
  }
}
