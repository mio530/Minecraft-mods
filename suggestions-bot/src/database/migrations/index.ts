import type BetterSqlite3 from 'better-sqlite3';

export interface Migration {
  version: number;
  name: string;
  up: (db: BetterSqlite3.Database) => void;
}

/**
 * Ordered migrations. Add new ones with an incrementing version; never edit
 * an already-released migration.
 */
export const migrations: Migration[] = [
  {
    version: 1,
    name: 'initial_schema',
    up: (db) => {
      db.exec(`
        CREATE TABLE IF NOT EXISTS guild_sequences (
          guild_id   TEXT PRIMARY KEY,
          next_value INTEGER NOT NULL DEFAULT 1
        );

        CREATE TABLE IF NOT EXISTS suggestions (
          id                TEXT PRIMARY KEY,
          guild_id          TEXT NOT NULL,
          forum_post_id     TEXT NOT NULL,
          author_id         TEXT NOT NULL,
          original_title    TEXT NOT NULL,
          original_desc     TEXT NOT NULL,
          ai_title          TEXT,
          ai_summary        TEXT,
          ai_status         TEXT NOT NULL DEFAULT 'pending',
          voting_message_id TEXT,
          voting_channel_id TEXT,
          status            TEXT NOT NULL DEFAULT 'pending',
          upvotes           INTEGER NOT NULL DEFAULT 0,
          downvotes         INTEGER NOT NULL DEFAULT 0,
          decided_by        TEXT,
          decision_reason   TEXT,
          voting_closes_at  INTEGER,
          voting_closed     INTEGER NOT NULL DEFAULT 0,
          created_at        INTEGER NOT NULL,
          updated_at        INTEGER NOT NULL
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_suggestions_forum_post
          ON suggestions (forum_post_id);
        CREATE INDEX IF NOT EXISTS idx_suggestions_voting_message
          ON suggestions (voting_message_id);
        CREATE INDEX IF NOT EXISTS idx_suggestions_status
          ON suggestions (status);
        CREATE INDEX IF NOT EXISTS idx_suggestions_expiry
          ON suggestions (voting_closed, voting_closes_at);

        CREATE TABLE IF NOT EXISTS votes (
          suggestion_id TEXT NOT NULL,
          user_id       TEXT NOT NULL,
          value         INTEGER NOT NULL,
          created_at    INTEGER NOT NULL,
          updated_at    INTEGER NOT NULL,
          PRIMARY KEY (suggestion_id, user_id),
          FOREIGN KEY (suggestion_id) REFERENCES suggestions (id) ON DELETE CASCADE
        );

        CREATE INDEX IF NOT EXISTS idx_votes_suggestion
          ON votes (suggestion_id);

        CREATE TABLE IF NOT EXISTS moderation_logs (
          id            INTEGER PRIMARY KEY AUTOINCREMENT,
          suggestion_id TEXT NOT NULL,
          staff_id      TEXT NOT NULL,
          action        TEXT NOT NULL,
          reason        TEXT,
          created_at    INTEGER NOT NULL,
          FOREIGN KEY (suggestion_id) REFERENCES suggestions (id) ON DELETE CASCADE
        );

        CREATE INDEX IF NOT EXISTS idx_modlogs_suggestion
          ON moderation_logs (suggestion_id);
      `);
    },
  },
];
