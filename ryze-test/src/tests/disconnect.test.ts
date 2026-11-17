import { EventEmitter } from 'node:events';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * Integration tests for disconnect capture: protocol phase tracking, the
 * reported "login ok but dropped in the configuration phase" case, category
 * discrimination (kick vs disconnect vs protocol-error vs timeout), and that a
 * single failure never produces duplicate botError emissions (no crash).
 */

process.env.DB_PATH = join(mkdtempSync(join(tmpdir(), 'ryze-disc-')), 'a.db');
process.env.MC_AUTH = 'offline';
process.env.MC_HOST = '127.0.0.1';
process.env.MC_PORT = '25565';
process.env.CONNECT_TIMEOUT_MS = '1000';
process.env.JWT_SECRET = 'disc_test_secret_1234567890';
process.env.MCP_TOKEN = 'disc-mcp-token';
process.env.DASHBOARD_PASSWORD = 'pw123456';

const g = globalThis as any;
vi.mock('mineflayer', () => {
  function createBot() {
    const bot: any = new EventEmitter();
    bot._client = new EventEmitter();
    bot._client.state = 'handshaking';
    bot.quit = () => {};
    bot.end = () => {};
    bot.setControlState = () => {};
    g.__bot = bot;
    if (g.__resolveBot) g.__resolveBot(bot);
    return bot;
  }
  return { default: { createBot }, createBot };
});

afterEach(() => {
  g.__bot = undefined;
});

function toState(bot: any, state: string) {
  bot._client.state = state;
  bot._client.emit('state', state);
}

function armBot(): Promise<any> {
  return new Promise((resolve) => {
    g.__resolveBot = resolve;
  });
}

describe('protocol phase + disconnect classification', () => {
  it('captures a pre-spawn drop in the CONFIGURATION phase as a disconnect', async () => {
    const { McBot } = await import('../mc/bot.js');
    const { BotConnectionError } = await import('../mc/errors.js');
    const bot = new McBot('d1');
    const ready = armBot();
    const p = bot.connect();
    const fake = await ready;
    fake._client.emit('connect');
    toState(fake, 'login');
    toState(fake, 'configuration');
    fake.emit('end', 'socketClosed'); // login succeeded, dropped before play
    await expect(p).rejects.toBeInstanceOf(BotConnectionError);
    expect(bot.errorDetail?.category).toBe('disconnect');
    expect(bot.errorDetail?.phase).toBe('configuration');
    expect(bot.errorDetail?.message).toMatch(/Configuration-Phase/);
  });

  it('classifies a server kick with the phase and parsed reason', async () => {
    const { McBot } = await import('../mc/bot.js');
    const bot = new McBot('d2');
    let count = 0;
    bot.on('botError', () => count++);
    const ready = armBot();
    const p = bot.connect().catch(() => {});
    const fake = await ready;
    fake._client.emit('connect');
    toState(fake, 'login');
    fake.emit('kicked', { text: 'Not whitelisted' });
    fake.emit('end', 'disconnect.kicked'); // kick is usually followed by end
    await p;
    expect(bot.errorDetail?.category).toBe('kicked');
    expect(bot.errorDetail?.message).toMatch(/Not whitelisted/);
    // dedup: kick + following end must NOT double-report
    expect(count).toBe(1);
  });

  it('classifies a protocol (de)serialization error during configuration', async () => {
    const { McBot } = await import('../mc/bot.js');
    const bot = new McBot('d3');
    const ready = armBot();
    const p = bot.connect().catch(() => {});
    const fake = await ready;
    fake._client.emit('connect');
    toState(fake, 'configuration');
    fake.emit('error', new Error('PartialReadError: Read error for undefined : Missing characters'));
    fake.emit('end', 'socketClosed');
    await p;
    expect(bot.errorDetail?.category).toBe('protocol_error');
    expect(bot.errorDetail?.phase).toBe('configuration');
  });

  it('a timeout after TCP connect reports the phase it stalled in', async () => {
    const { McBot } = await import('../mc/bot.js');
    const bot = new McBot('d4');
    const ready = armBot();
    const p = bot.connect();
    const fake = await ready;
    fake._client.emit('connect');
    toState(fake, 'login');
    // never spawn, never end -> CONNECT_TIMEOUT_MS fires
    const err = await p.then(() => null, (e) => e);
    expect(err).toBeTruthy();
    expect(bot.errorDetail?.phase).toBe('login');
    expect(['timeout', 'handshake']).toContain(bot.errorDetail?.category);
  }, 5000);

  it('does not emit duplicate botError for the same error followed by end', async () => {
    const { McBot } = await import('../mc/bot.js');
    const bot = new McBot('d5');
    let count = 0;
    bot.on('botError', () => count++);
    const ready = armBot();
    const p = bot.connect().catch(() => {});
    const fake = await ready;
    fake._client.emit('connect');
    toState(fake, 'login');
    const e = Object.assign(new Error('connect ECONNRESET'), { code: 'ECONNRESET' });
    fake.emit('error', e);
    fake.emit('end', 'ECONNRESET');
    await p;
    expect(count).toBe(1);
  });
});
