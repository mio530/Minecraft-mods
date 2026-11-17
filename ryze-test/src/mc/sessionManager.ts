import { EventEmitter } from 'node:events';
import { randomUUID } from 'node:crypto';
import { config, isDangerous } from '../config.js';
import { logger } from '../logger.js';
import { audit } from '../security/audit.js';
import { startLogin, status as authStatus } from '../auth/microsoft.js';
import { McBot } from './bot.js';
import { ClassifiedError } from './errors.js';
import { Observation } from './types.js';

export interface PendingApproval {
  id: string;
  sessionId: string;
  message: string;
  requestedBy: string;
  requestedAt: number;
}

interface Session {
  id: string;
  bot: McBot;
  startedAt: number;
  lastActivity: number;
  timeout: NodeJS.Timeout;
  tick: NodeJS.Timeout;
}

/**
 * Owns the lifecycle of test sessions. Enforces MAX_SESSIONS, session
 * timeouts, broadcasts live observations, and gates dangerous commands
 * behind explicit owner approval.
 */
export class SessionManager extends EventEmitter {
  private sessions = new Map<string, Session>();
  private approvals = new Map<string, PendingApproval & { resolve: (ok: boolean) => void }>();
  private _lastError: ClassifiedError | null = null;

  get lastError(): ClassifiedError | null {
    return this._lastError;
  }

  list(): { id: string; startedAt: number; connected: boolean }[] {
    return [...this.sessions.values()].map((s) => ({
      id: s.id,
      startedAt: s.startedAt,
      connected: s.bot.connected,
    }));
  }

  current(): Session | null {
    return this.sessions.values().next().value ?? null;
  }

  get(id: string): Session | null {
    return this.sessions.get(id) ?? null;
  }

  /** Resolve a session by id, or the single current one when id omitted. */
  resolve(id?: string): McBot {
    const s = id ? this.get(id) : this.current();
    if (!s) throw new Error('no active session');
    s.lastActivity = Date.now();
    return s.bot;
  }

  async start(requestedBy: string): Promise<{ id: string; observation: Observation }> {
    if (this.sessions.size >= config.MAX_SESSIONS) {
      throw new Error(`max sessions reached (${config.MAX_SESSIONS}); stop one first`);
    }
    if (config.MC_AUTH === 'microsoft' && !authStatus().authenticated) {
      // Kick off device-code login; caller should poll /api/auth/status.
      startLogin();
      throw new Error('microsoft_login_required');
    }

    const id = randomUUID().slice(0, 8);
    const bot = new McBot(id);

    bot.on('chat', () => this.touch(id));
    // Consume 'botError' so an absent listener can never crash the process.
    bot.on('botError', (e: ClassifiedError) => {
      this._lastError = e;
      logger.warn({ id, category: e.category, phase: e.phase, retryable: e.retryable }, 'bot error');
      audit('system', id, 'session.bot_error', 'error', id, `${e.category}/${e.phase}: ${e.message}`);
      // Forward to the dashboard live console via the WS layer.
      this.emit('bot_error', { sessionId: id, ...e });
    });
    bot.on('end', (reason: string) => {
      logger.warn({ id }, 'session bot ended');
      audit('system', id, 'session.bot_end', 'ok', id, String(reason));
      this.stop(id, 'bot disconnected').catch(() => {});
    });

    try {
      await bot.connect();
    } catch (err) {
      const classified = (bot.errorDetail ?? null) as ClassifiedError | null;
      if (classified) this._lastError = classified;
      audit('system', requestedBy, 'session.start', 'error', id, classified?.message ?? String(err));
      try {
        bot.disconnect('connect failed');
      } catch {
        /* ignore */
      }
      throw err; // propagated to REST/MCP; dashboard shows the classified message
    }
    this._lastError = null; // connected successfully

    const session: Session = {
      id,
      bot,
      startedAt: Date.now(),
      lastActivity: Date.now(),
      timeout: setInterval(() => this.checkTimeout(id), 30_000),
      tick: setInterval(() => {
        if (bot.connected) this.emit('observation', bot.observe());
      }, 1000),
    };
    this.sessions.set(id, session);
    audit('system', requestedBy, 'session.start', 'ok', id);
    logger.info({ id }, 'session started');
    return { id, observation: bot.observe() };
  }

  /**
   * TEST ONLY: register a pre-built (e.g. fake-backed) bot as a live session,
   * bypassing the real network connect(). Never used in production.
   */
  injectForTest(bot: McBot): string {
    const id = bot.sessionId;
    const session: Session = {
      id,
      bot,
      startedAt: Date.now(),
      lastActivity: Date.now(),
      timeout: setInterval(() => this.checkTimeout(id), 30_000),
      tick: setInterval(() => {
        if (bot.connected) this.emit('observation', bot.observe());
      }, 1000),
    };
    session.timeout.unref?.();
    session.tick.unref?.();
    this.sessions.set(id, session);
    return id;
  }

  async stop(id: string, reason = 'stopped by request'): Promise<void> {
    const s = this.sessions.get(id);
    if (!s) return;
    clearInterval(s.timeout);
    clearInterval(s.tick);
    s.bot.disconnect(reason);
    this.sessions.delete(id);
    audit('system', id, 'session.stop', 'ok', id, reason);
    this.emit('session_closed', id);
    logger.info({ id, reason }, 'session stopped');
  }

  async stopAll(): Promise<void> {
    await Promise.all([...this.sessions.keys()].map((id) => this.stop(id, 'shutdown')));
  }

  private touch(id: string): void {
    const s = this.sessions.get(id);
    if (s) s.lastActivity = Date.now();
  }

  private checkTimeout(id: string): void {
    const s = this.sessions.get(id);
    if (!s) return;
    if (Date.now() - s.lastActivity > config.SESSION_TIMEOUT_MS) {
      logger.info({ id }, 'session idle timeout');
      this.stop(id, 'idle timeout').catch(() => {});
    }
  }

  // --- dangerous-command approval flow ---

  /**
   * Request to send a chat/command. If it's a dangerous command in safe mode
   * and the requester lacks admin, it is queued for owner approval and this
   * returns { approvalRequired: true }.
   */
  async requestChat(
    sessionId: string | undefined,
    message: string,
    requestedBy: string,
    hasAdmin: boolean,
  ): Promise<{ sent: boolean; approvalRequired: boolean; approvalId?: string }> {
    const bot = this.resolve(sessionId);
    if (isDangerous(message) && !hasAdmin) {
      const approvalId = randomUUID().slice(0, 8);
      const pending: PendingApproval & { resolve: (ok: boolean) => void } = {
        id: approvalId,
        sessionId: bot.sessionId,
        message,
        requestedBy,
        requestedAt: Date.now(),
        resolve: () => {},
      };
      this.approvals.set(approvalId, pending);
      audit('ai', requestedBy, 'chat.approval_requested', 'denied', bot.sessionId, message);
      this.emit('approval_requested', { id: approvalId, sessionId: bot.sessionId, message, requestedBy, requestedAt: pending.requestedAt });
      return { sent: false, approvalRequired: true, approvalId };
    }
    await bot.sendChat(message);
    audit(hasAdmin ? 'owner' : 'ai', requestedBy, 'chat.send', 'ok', bot.sessionId, message);
    return { sent: true, approvalRequired: false };
  }

  pendingApprovals(): PendingApproval[] {
    return [...this.approvals.values()].map(({ resolve, ...rest }) => rest);
  }

  async resolveApproval(approvalId: string, approve: boolean, by: string): Promise<boolean> {
    const p = this.approvals.get(approvalId);
    if (!p) return false;
    this.approvals.delete(approvalId);
    if (approve) {
      try {
        const bot = this.resolve(p.sessionId);
        await bot.sendChat(p.message);
        audit('owner', by, 'chat.approved', 'ok', p.sessionId, p.message);
      } catch (err) {
        audit('owner', by, 'chat.approved', 'error', p.sessionId, String(err));
        return false;
      }
    } else {
      audit('owner', by, 'chat.denied', 'denied', p.sessionId, p.message);
    }
    this.emit('approval_resolved', { id: approvalId, approve });
    return true;
  }
}

export const sessions = new SessionManager();
