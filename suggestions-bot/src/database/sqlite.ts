import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import BetterSqlite3 from 'better-sqlite3';
import { logger } from '../utils/logger.js';
import type {
  ModerationLog,
  Suggestion,
  SuggestionStatus,
  Vote,
  VoteTotals,
  VoteValue,
} from '../types/index.js';
import { migrations } from './migrations/index.js';
import type { LeaderboardEntry, SuggestionStats, SuggestionStore } from './store.js';

interface SuggestionRow {
  id: string;
  guild_id: string;
  forum_post_id: string;
  author_id: string;
  original_title: string;
  original_desc: string;
  ai_title: string | null;
  ai_summary: string | null;
  ai_status: string;
  voting_message_id: string | null;
  voting_channel_id: string | null;
  status: string;
  upvotes: number;
  downvotes: number;
  decided_by: string | null;
  decision_reason: string | null;
  voting_closes_at: number | null;
  voting_closed: number;
  created_at: number;
  updated_at: number;
}

function toSuggestion(r: SuggestionRow): Suggestion {
  return {
    id: r.id,
    guildId: r.guild_id,
    forumPostId: r.forum_post_id,
    authorId: r.author_id,
    originalTitle: r.original_title,
    originalDescription: r.original_desc,
    aiTitle: r.ai_title,
    aiSummary: r.ai_summary,
    aiStatus: r.ai_status as Suggestion['aiStatus'],
    votingMessageId: r.voting_message_id,
    votingChannelId: r.voting_channel_id,
    status: r.status as SuggestionStatus,
    upvotes: r.upvotes,
    downvotes: r.downvotes,
    decidedBy: r.decided_by,
    decisionReason: r.decision_reason,
    votingClosesAt: r.voting_closes_at,
    votingClosed: (r.voting_closed ? 1 : 0) as 0 | 1,
    createdAt: r.created_at,
    updatedAt: r.updated_at,
  };
}

function computeTotals(up: number, down: number): VoteTotals {
  const total = up + down;
  const positiveRatio = total === 0 ? 0 : Math.round((up / total) * 1000) / 10;
  return { upvotes: up, downvotes: down, total, positiveRatio };
}

export class SqliteStore implements SuggestionStore {
  private db: BetterSqlite3.Database;

  constructor(path: string) {
    if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
    this.db = new BetterSqlite3(path);
    this.db.pragma('journal_mode = WAL');
    this.db.pragma('foreign_keys = ON');
    this.db.pragma('busy_timeout = 5000');
    this.migrate();
  }

  private migrate(): void {
    this.db.exec(
      `CREATE TABLE IF NOT EXISTS schema_migrations (
         version INTEGER PRIMARY KEY,
         name    TEXT NOT NULL,
         applied_at INTEGER NOT NULL
       );`,
    );
    const applied = new Set(
      this.db
        .prepare('SELECT version FROM schema_migrations')
        .all()
        .map((r) => (r as { version: number }).version),
    );
    for (const m of migrations) {
      if (applied.has(m.version)) continue;
      const tx = this.db.transaction(() => {
        m.up(this.db);
        this.db
          .prepare('INSERT INTO schema_migrations (version, name, applied_at) VALUES (?, ?, ?)')
          .run(m.version, m.name, Date.now());
      });
      tx();
      logger.info(`Applied migration ${m.version}: ${m.name}`);
    }
  }

  nextSequence(guildId: string): number {
    const tx = this.db.transaction((g: string) => {
      this.db
        .prepare('INSERT OR IGNORE INTO guild_sequences (guild_id, next_value) VALUES (?, 1)')
        .run(g);
      const row = this.db
        .prepare('SELECT next_value FROM guild_sequences WHERE guild_id = ?')
        .get(g) as { next_value: number };
      this.db
        .prepare('UPDATE guild_sequences SET next_value = next_value + 1 WHERE guild_id = ?')
        .run(g);
      return row.next_value;
    });
    return tx(guildId);
  }

  createSuggestion(s: Suggestion): void {
    this.db
      .prepare(
        `INSERT INTO suggestions (
           id, guild_id, forum_post_id, author_id, original_title, original_desc,
           ai_title, ai_summary, ai_status, voting_message_id, voting_channel_id,
           status, upvotes, downvotes, decided_by, decision_reason,
           voting_closes_at, voting_closed, created_at, updated_at
         ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
      )
      .run(
        s.id,
        s.guildId,
        s.forumPostId,
        s.authorId,
        s.originalTitle,
        s.originalDescription,
        s.aiTitle,
        s.aiSummary,
        s.aiStatus,
        s.votingMessageId,
        s.votingChannelId,
        s.status,
        s.upvotes,
        s.downvotes,
        s.decidedBy,
        s.decisionReason,
        s.votingClosesAt,
        s.votingClosed,
        s.createdAt,
        s.updatedAt,
      );
  }

  getSuggestion(id: string): Suggestion | null {
    const r = this.db.prepare('SELECT * FROM suggestions WHERE id = ?').get(id) as
      | SuggestionRow
      | undefined;
    return r ? toSuggestion(r) : null;
  }

  getByForumPost(forumPostId: string): Suggestion | null {
    const r = this.db
      .prepare('SELECT * FROM suggestions WHERE forum_post_id = ?')
      .get(forumPostId) as SuggestionRow | undefined;
    return r ? toSuggestion(r) : null;
  }

  getByVotingMessage(messageId: string): Suggestion | null {
    const r = this.db
      .prepare('SELECT * FROM suggestions WHERE voting_message_id = ?')
      .get(messageId) as SuggestionRow | undefined;
    return r ? toSuggestion(r) : null;
  }

  setVotingMessage(id: string, channelId: string, messageId: string): void {
    this.db
      .prepare(
        'UPDATE suggestions SET voting_channel_id = ?, voting_message_id = ?, updated_at = ? WHERE id = ?',
      )
      .run(channelId, messageId, Date.now(), id);
  }

  updateAi(
    id: string,
    aiTitle: string,
    aiSummary: string,
    aiStatus: Suggestion['aiStatus'],
  ): void {
    this.db
      .prepare(
        'UPDATE suggestions SET ai_title = ?, ai_summary = ?, ai_status = ?, updated_at = ? WHERE id = ?',
      )
      .run(aiTitle, aiSummary, aiStatus, Date.now(), id);
  }

  updateStatus(
    id: string,
    status: SuggestionStatus,
    decidedBy: string | null,
    reason: string | null,
  ): void {
    this.db
      .prepare(
        'UPDATE suggestions SET status = ?, decided_by = ?, decision_reason = ?, updated_at = ? WHERE id = ?',
      )
      .run(status, decidedBy, reason, Date.now(), id);
  }

  setVotingClosed(id: string, closed: boolean): void {
    this.db
      .prepare('UPDATE suggestions SET voting_closed = ?, updated_at = ? WHERE id = ?')
      .run(closed ? 1 : 0, Date.now(), id);
  }

  setVotingClosesAt(id: string, at: number | null): void {
    this.db
      .prepare('UPDATE suggestions SET voting_closes_at = ?, updated_at = ? WHERE id = ?')
      .run(at, Date.now(), id);
  }

  listByStatus(status: SuggestionStatus): Suggestion[] {
    return (
      this.db
        .prepare('SELECT * FROM suggestions WHERE status = ? ORDER BY created_at DESC')
        .all(status) as SuggestionRow[]
    ).map(toSuggestion);
  }

  listExpired(now: number): Suggestion[] {
    return (
      this.db
        .prepare(
          `SELECT * FROM suggestions
             WHERE voting_closed = 0
               AND voting_closes_at IS NOT NULL
               AND voting_closes_at <= ?`,
        )
        .all(now) as SuggestionRow[]
    ).map(toSuggestion);
  }

  stats(): SuggestionStats {
    const row = this.db
      .prepare(
        `SELECT
           COUNT(*) AS total,
           SUM(status = 'pending')     AS pending,
           SUM(status = 'approved')    AS approved,
           SUM(status = 'rejected')    AS rejected,
           SUM(status = 'implemented') AS implemented,
           SUM(status = 'closed')      AS closed
         FROM suggestions`,
      )
      .get() as Record<string, number | null>;
    return {
      total: row.total ?? 0,
      pending: row.pending ?? 0,
      approved: row.approved ?? 0,
      rejected: row.rejected ?? 0,
      implemented: row.implemented ?? 0,
      closed: row.closed ?? 0,
    };
  }

  leaderboard(limit: number): LeaderboardEntry[] {
    const rows = this.db
      .prepare(
        `SELECT *, (upvotes - downvotes) AS score
           FROM suggestions
           ORDER BY score DESC, upvotes DESC, created_at ASC
           LIMIT ?`,
      )
      .all(limit) as (SuggestionRow & { score: number })[];
    return rows.map((r) => ({ suggestion: toSuggestion(r), score: r.score }));
  }

  recentForDuplicateCheck(limit: number): Suggestion[] {
    return (
      this.db
        .prepare('SELECT * FROM suggestions ORDER BY created_at DESC LIMIT ?')
        .all(limit) as SuggestionRow[]
    ).map(toSuggestion);
  }

  private recount(suggestionId: string): VoteTotals {
    const row = this.db
      .prepare(
        `SELECT
           COALESCE(SUM(value = 1), 0)  AS up,
           COALESCE(SUM(value = -1), 0) AS down
         FROM votes WHERE suggestion_id = ?`,
      )
      .get(suggestionId) as { up: number; down: number };
    this.db
      .prepare('UPDATE suggestions SET upvotes = ?, downvotes = ?, updated_at = ? WHERE id = ?')
      .run(row.up, row.down, Date.now(), suggestionId);
    return computeTotals(row.up, row.down);
  }

  castVote(suggestionId: string, userId: string, value: VoteValue): VoteTotals {
    const tx = this.db.transaction(() => {
      const now = Date.now();
      this.db
        .prepare(
          `INSERT INTO votes (suggestion_id, user_id, value, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?)
           ON CONFLICT (suggestion_id, user_id)
             DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
        )
        .run(suggestionId, userId, value, now, now);
      return this.recount(suggestionId);
    });
    return tx();
  }

  removeVote(suggestionId: string, userId: string): VoteTotals {
    const tx = this.db.transaction(() => {
      this.db
        .prepare('DELETE FROM votes WHERE suggestion_id = ? AND user_id = ?')
        .run(suggestionId, userId);
      return this.recount(suggestionId);
    });
    return tx();
  }

  getVote(suggestionId: string, userId: string): Vote | null {
    const r = this.db
      .prepare('SELECT * FROM votes WHERE suggestion_id = ? AND user_id = ?')
      .get(suggestionId, userId) as
      | { suggestion_id: string; user_id: string; value: number; created_at: number; updated_at: number }
      | undefined;
    if (!r) return null;
    return {
      suggestionId: r.suggestion_id,
      userId: r.user_id,
      value: r.value as VoteValue,
      createdAt: r.created_at,
      updatedAt: r.updated_at,
    };
  }

  getTotals(suggestionId: string): VoteTotals {
    const s = this.getSuggestion(suggestionId);
    if (!s) return computeTotals(0, 0);
    return computeTotals(s.upvotes, s.downvotes);
  }

  addModLog(entry: ModerationLog): void {
    this.db
      .prepare(
        'INSERT INTO moderation_logs (suggestion_id, staff_id, action, reason, created_at) VALUES (?,?,?,?,?)',
      )
      .run(entry.suggestionId, entry.staffId, entry.action, entry.reason, entry.createdAt);
  }

  listModLogs(suggestionId: string): ModerationLog[] {
    const rows = this.db
      .prepare('SELECT * FROM moderation_logs WHERE suggestion_id = ? ORDER BY created_at ASC')
      .all(suggestionId) as {
      id: number;
      suggestion_id: string;
      staff_id: string;
      action: string;
      reason: string | null;
      created_at: number;
    }[];
    return rows.map((r) => ({
      id: r.id,
      suggestionId: r.suggestion_id,
      staffId: r.staff_id,
      action: r.action,
      reason: r.reason,
      createdAt: r.created_at,
    }));
  }

  close(): void {
    this.db.close();
  }
}
