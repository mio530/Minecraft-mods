import { SqliteStore } from './sqlite.js';
import type { SuggestionStore } from './store.js';

export type { SuggestionStore, SuggestionStats, LeaderboardEntry } from './store.js';

/**
 * Create the persistence store. Swap this factory to add a PostgreSQL backend
 * later — the rest of the bot only depends on the SuggestionStore interface.
 */
export function createStore(databasePath: string): SuggestionStore {
  return new SqliteStore(databasePath);
}
