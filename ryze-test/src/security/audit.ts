import Database from 'better-sqlite3';
import { existsSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { config } from '../config.js';
import { logger } from '../logger.js';

/**
 * Append-only audit log for every observation/control action, stored in SQLite.
 * Lets the owner review exactly what the AI (or a human) did and when.
 */
export type Actor = 'owner' | 'ai' | 'system';

export interface AuditEntry {
  id: number;
  ts: number;
  actor: Actor;
  actorId: string; // session id, token id, or 'system'
  action: string;
  target: string | null;
  detail: string | null;
  result: 'ok' | 'denied' | 'error';
}

let db: Database.Database;

export function initAudit(): void {
  const dir = dirname(config.DB_PATH);
  if (dir && !existsSync(dir)) mkdirSync(dir, { recursive: true });
  db = new Database(config.DB_PATH);
  db.pragma('journal_mode = WAL');
  db.exec(`
    CREATE TABLE IF NOT EXISTS audit (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      ts INTEGER NOT NULL,
      actor TEXT NOT NULL,
      actorId TEXT NOT NULL,
      action TEXT NOT NULL,
      target TEXT,
      detail TEXT,
      result TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_audit_ts ON audit(ts);
    CREATE TABLE IF NOT EXISTS test_results (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      ts INTEGER NOT NULL,
      sessionId TEXT NOT NULL,
      name TEXT NOT NULL,
      status TEXT NOT NULL,
      expected TEXT,
      actual TEXT,
      detail TEXT
    );
  `);
  logger.info({ db: config.DB_PATH }, 'audit store ready');
}

export function audit(
  actor: Actor,
  actorId: string,
  action: string,
  result: 'ok' | 'denied' | 'error' = 'ok',
  target: string | null = null,
  detail: string | null = null,
): void {
  try {
    db.prepare(
      'INSERT INTO audit (ts, actor, actorId, action, target, detail, result) VALUES (?,?,?,?,?,?,?)',
    ).run(Date.now(), actor, actorId, action, target, detail, result);
  } catch (err) {
    logger.error({ err }, 'audit write failed');
  }
}

export function recentAudit(limit = 100): AuditEntry[] {
  return db
    .prepare('SELECT * FROM audit ORDER BY id DESC LIMIT ?')
    .all(Math.min(limit, 1000)) as AuditEntry[];
}

export interface TestResultRow {
  id: number;
  ts: number;
  sessionId: string;
  name: string;
  status: 'pass' | 'fail' | 'error' | 'skipped';
  expected: string | null;
  actual: string | null;
  detail: string | null;
}

export function recordTestResult(r: Omit<TestResultRow, 'id' | 'ts'>): void {
  db.prepare(
    'INSERT INTO test_results (ts, sessionId, name, status, expected, actual, detail) VALUES (?,?,?,?,?,?,?)',
  ).run(Date.now(), r.sessionId, r.name, r.status, r.expected, r.actual, r.detail);
}

export function recentTestResults(limit = 100): TestResultRow[] {
  return db
    .prepare('SELECT * FROM test_results ORDER BY id DESC LIMIT ?')
    .all(Math.min(limit, 1000)) as TestResultRow[];
}
