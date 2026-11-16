import type {
  GuildSettings,
  ModerationLog,
  Suggestion,
  SuggestionStatus,
  Vote,
  VoteTotals,
  VoteValue,
} from '../types/index.js';

export interface SuggestionStats {
  total: number;
  pending: number;
  approved: number;
  rejected: number;
  implemented: number;
  closed: number;
}

export interface LeaderboardEntry {
  suggestion: Suggestion;
  score: number;
}

/**
 * Persistence abstraction. The default implementation is SQLite, but any
 * backend (e.g. PostgreSQL) can implement this interface without touching
 * the rest of the bot.
 */
export interface SuggestionStore {
  /** Reserve and return the next numeric sequence for a guild. Atomic. */
  nextSequence(guildId: string): number;

  createSuggestion(s: Suggestion): void;
  getSuggestion(id: string): Suggestion | null;
  getByForumPost(forumPostId: string): Suggestion | null;
  getByVotingMessage(messageId: string): Suggestion | null;

  setVotingMessage(id: string, channelId: string, messageId: string): void;
  updateAi(id: string, aiTitle: string, aiSummary: string, aiStatus: Suggestion['aiStatus']): void;
  updateStatus(
    id: string,
    status: SuggestionStatus,
    decidedBy: string | null,
    reason: string | null,
  ): void;
  setVotingClosed(id: string, closed: boolean): void;
  setVotingClosesAt(id: string, at: number | null): void;

  listByStatus(status: SuggestionStatus): Suggestion[];
  listExpired(now: number): Suggestion[];
  stats(): SuggestionStats;
  leaderboard(limit: number): LeaderboardEntry[];
  recentForDuplicateCheck(limit: number): Suggestion[];

  /**
   * Cast or change a vote and return the recomputed totals. Transactional.
   * Passing the same value the user already has is a no-op (returns totals).
   */
  castVote(suggestionId: string, userId: string, value: VoteValue): VoteTotals;
  /** Remove a user's vote and return recomputed totals. */
  removeVote(suggestionId: string, userId: string): VoteTotals;
  getVote(suggestionId: string, userId: string): Vote | null;
  getTotals(suggestionId: string): VoteTotals;

  addModLog(entry: ModerationLog): void;
  listModLogs(suggestionId: string): ModerationLog[];

  getGuildSettings(guildId: string): GuildSettings | null;
  /** Upsert the given fields; omitted/undefined fields are left unchanged. */
  saveGuildSettings(
    guildId: string,
    patch: Partial<Omit<GuildSettings, 'guildId' | 'updatedAt'>>,
  ): GuildSettings;

  close(): void;
}
