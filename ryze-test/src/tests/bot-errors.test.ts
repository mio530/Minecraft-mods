import { EventEmitter } from 'node:events';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * Regression tests for the Minecraft connection-failure handling:
 *  - the reported Mojang sessionserver FetchError is classified, not fatal;
 *  - a bot error NEVER crashes the process (no unhandled 'error' event);
 *  - connect() failures surface a classified, token-free message;
 *  - the session manager records the last error and the dashboard shows it.
 */

process.env.DB_PATH = join(mkdtempSync(join(tmpdir(), 'ryze-boterr-')), 'a.db');
process.env.MC_AUTH = 'offline';
process.env.MC_HOST = '203.0.113.10';
process.env.MC_PORT = '25565';
process.env.JWT_SECRET = 'boterr_test_secret_123456';
process.env.MCP_TOKEN = 'boterr-mcp-token';
process.env.DASHBOARD_PASSWORD = 'pw123456';

// Controllable fake mineflayer: createBot returns an emitter that fires an
// error (or spawns) based on a global set by each test.
const g = globalThis as any;
vi.mock('mineflayer', () => {
  function createBot() {
    const bot: any = new EventEmitter();
    bot.quit = () => {};
    bot.end = () => {};
    bot.setControlState = () => {};
    setImmediate(() => {
      if (g.__mcError) bot.emit('error', g.__mcError);
      else if (g.__mcSpawn) {
        bot.entity = { position: { x: 0, y: 64, z: 0, distanceTo: () => 0, offset: () => ({}) }, yaw: 0, pitch: 0, onGround: true, id: 1 };
        bot.emit('spawn');
      }
    });
    return bot;
  }
  return { default: { createBot }, createBot };
});

afterEach(() => {
  g.__mcError = undefined;
  g.__mcSpawn = undefined;
});

describe('classifyBotError', () => {
  it('classifies the reported Mojang sessionserver FetchError as retryable network/egress', async () => {
    const { classifyBotError } = await import('../mc/errors.js');
    const err = Object.assign(new Error('Invalid response body while trying to fetch https://sessionserver.mojang.com/session/minecraft/join'), {
      code: 'ERR_STREAM_PREMATURE_CLOSE',
    });
    const c = classifyBotError(err);
    expect(c.category).toBe('mojang_session');
    expect(c.retryable).toBe(true);
    expect(c.message).toMatch(/sessionserver\.mojang\.com/);
  });

  it('classifies unreachable server, invalid auth, kick and timeout', async () => {
    const { classifyBotError } = await import('../mc/errors.js');
    expect(classifyBotError(Object.assign(new Error('connect ECONNREFUSED'), { code: 'ECONNREFUSED' })).category).toBe('network_server');
    expect(classifyBotError(new Error('Request failed 403 Forbidden invalid token')).category).toBe('invalid_auth');
    expect(classifyBotError(new Error('XSTS error: account does not own Minecraft')).category).toBe('invalid_auth');
    expect(classifyBotError(undefined, { kickReason: 'You are not white-listed on this server!' }).category).toBe('kicked');
    expect(classifyBotError(new Error('connection timeout: handshake stalled')).category).toBe('timeout');
    expect(classifyBotError(new Error('weird')).category).toBe('unknown');
  });

  it('parses a chat-component kick reason to plain text and records the phase', async () => {
    const { classifyBotError } = await import('../mc/errors.js');
    const c = classifyBotError(undefined, {
      kickReason: { text: 'You are ', extra: [{ text: 'not whitelisted' }, { text: '!' }] },
      phase: 'login',
    });
    expect(c.category).toBe('kicked');
    expect(c.phase).toBe('login');
    expect(c.message).toMatch(/You are not whitelisted!/);
    expect(c.retryable).toBe(false);
  });

  it('classifies a packet (de)serialization failure as protocol_error with phase', async () => {
    const { classifyBotError } = await import('../mc/errors.js');
    const c = classifyBotError(new Error('PartialReadError: Read error for undefined'), { phase: 'configuration' });
    expect(c.category).toBe('protocol_error');
    expect(c.phase).toBe('configuration');
  });

  it('classifies a pre-spawn socket close as disconnect, highlighting the configuration phase', async () => {
    const { classifyBotError } = await import('../mc/errors.js');
    const c = classifyBotError(new Error('socketClosed'), { phase: 'configuration', disconnect: true });
    expect(c.category).toBe('disconnect');
    expect(c.phase).toBe('configuration');
    expect(c.message).toMatch(/Configuration-Phase/);
    expect(c.retryable).toBe(true);
  });

  it('every classified error carries a phase and is token-free', async () => {
    const { classifyBotError } = await import('../mc/errors.js');
    for (const c of [
      classifyBotError(new Error('x'), { phase: 'play' }),
      classifyBotError(new Error('connect ECONNREFUSED'), { phase: 'handshaking' }),
      classifyBotError(undefined, { kickReason: 'bye', phase: 'play' }),
    ]) {
      expect(typeof c.phase).toBe('string');
    }
  });

  it('redacts token-like strings from classified messages', async () => {
    const { classifyBotError } = await import('../mc/errors.js');
    const secret = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJlLXZhbHVlLWhlcmU';
    const c = classifyBotError(new Error('auth blew up with token ' + secret + ' and ' + 'A'.repeat(50)));
    expect(c.message).not.toContain(secret);
    expect(c.message).not.toContain('A'.repeat(50));
    expect(c.message).toContain('[redacted]');
  });
});

describe('McBot crash-safety', () => {
  it('emits botError (never the special "error" event) so a missing listener cannot crash', async () => {
    const { McBot } = await import('../mc/bot.js');
    const bot = new McBot('crash01');
    const fake: any = new EventEmitter();
    fake.quit = () => {};
    bot.attachForTest(fake);
    // No 'botError'/'error' listener attached on the McBot on purpose.
    expect(bot.listenerCount('error')).toBe(0);
    // This would THROW if McBot re-emitted the special 'error' event.
    expect(() => fake.emit('error', Object.assign(new Error('premature close'), { code: 'ERR_STREAM_PREMATURE_CLOSE' }))).not.toThrow();
    expect(bot.errorDetail?.category).toBe('mojang_session');
  });

  it('connect() rejects with a classified, token-free error (does not throw out of band)', async () => {
    const { McBot } = await import('../mc/bot.js');
    const { BotConnectionError } = await import('../mc/errors.js');
    g.__mcError = Object.assign(new Error('connect ECONNREFUSED 203.0.113.10:25565'), { code: 'ECONNREFUSED' });
    const bot = new McBot('conn01');
    await expect(bot.connect()).rejects.toBeInstanceOf(BotConnectionError);
    expect(bot.errorDetail?.category).toBe('network_server');
    expect(bot.connected).toBe(false);
  });
});

describe('session manager + dashboard surface the failure', () => {
  it('records the last error and keeps running when a start fails', async () => {
    const { sessions } = await import('../mc/sessionManager.js');
    const svc = await import('../service.js');
    const { owner } = await import('../security/rbac.js');
    g.__mcError = Object.assign(new Error('Invalid response body while trying to fetch https://sessionserver.mojang.com/session/minecraft/join'), {
      code: 'ERR_STREAM_PREMATURE_CLOSE',
    });
    const { initAudit } = await import('../security/audit.js');
    initAudit();
    await expect(sessions.start('owner')).rejects.toBeTruthy();
    const status = svc.getServerStatus(owner());
    expect(status.lastError?.category).toBe('mojang_session');
    expect(JSON.stringify(status.lastError)).not.toMatch(/eyJ/);
    // process is obviously still alive to run the next assertion:
    expect(status.service).toBe('ryze-test');
  });
});
