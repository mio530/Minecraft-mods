import { EventEmitter } from 'node:events';
import { afterAll, describe, expect, it, vi } from 'vitest';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * Regression tests for the "unsupported protocol version: auto" bug.
 * The target server is Minecraft Java 1.21.11; MC_VERSION must resolve to a
 * supported concrete version and mineflayer must receive 1.21.11, never "auto".
 */

// Reproduce the exact misconfiguration that caused the crash.
process.env.MC_VERSION = 'auto';
process.env.MC_AUTH = 'offline';
process.env.MC_HOST = '127.0.0.1';
process.env.MC_PORT = '25571';
process.env.DB_PATH = join(mkdtempSync(join(tmpdir(), 'ryze-ver-')), 'a.db');
process.env.JWT_SECRET = 'version_test_secret_123456';
process.env.MCP_TOKEN = 'version-mcp-token';
process.env.DASHBOARD_PASSWORD = 'pw123456';

const g = globalThis as any;
vi.mock('mineflayer', () => {
  function createBot(opts: any) {
    g.__createBotVersion = opts.version;
    const bot: any = new EventEmitter();
    bot.quit = () => {};
    bot.end = () => {};
    bot.setControlState = () => {};
    setImmediate(() => {
      bot.entity = { position: { x: 0, y: 64, z: 0 }, yaw: 0, pitch: 0, onGround: true, id: 1 };
      bot.emit('spawn');
    });
    return bot;
  }
  return { default: { createBot }, createBot };
});

afterAll(() => {
  g.__createBotVersion = undefined;
});

describe('resolveMcVersion / isSupportedMcVersion', () => {
  it('keeps the supported target version 1.21.11', async () => {
    const { resolveMcVersion, isSupportedMcVersion, DEFAULT_MC_VERSION } = await import('../mc/version.js');
    expect(DEFAULT_MC_VERSION).toBe('1.21.11');
    expect(isSupportedMcVersion('1.21.11')).toBe(true);
    expect(resolveMcVersion('1.21.11')).toBe('1.21.11');
  });

  it('falls back to 1.21.11 for "auto", empty, null and unknown versions, with a warning', async () => {
    const { resolveMcVersion } = await import('../mc/version.js');
    const warns: string[] = [];
    const w = (m: string) => warns.push(m);
    expect(resolveMcVersion('auto', w)).toBe('1.21.11');
    expect(resolveMcVersion('', w)).toBe('1.21.11');
    expect(resolveMcVersion(null, w)).toBe('1.21.11');
    expect(resolveMcVersion('1.999.0', w)).toBe('1.21.11');
    expect(resolveMcVersion('garbage', w)).toBe('1.21.11');
    // 'auto', '1.999.0' and 'garbage' are invalid and should warn; '' and null do not.
    expect(warns.length).toBe(3);
    expect(warns[0]).toMatch(/auto/);
  });

  it('rejects clearly invalid version strings', async () => {
    const { isSupportedMcVersion } = await import('../mc/version.js');
    expect(isSupportedMcVersion('auto')).toBe(false);
    expect(isSupportedMcVersion('')).toBe(false);
    expect(isSupportedMcVersion('latest')).toBe(false);
  });
});

describe('config + bot use a supported version', () => {
  it('normalises MC_VERSION="auto" to 1.21.11 in config', async () => {
    const { config } = await import('../config.js');
    expect(config.MC_VERSION).toBe('1.21.11');
  });

  it('passes 1.21.11 (never "auto" or false) to mineflayer.createBot', async () => {
    const { McBot } = await import('../mc/bot.js');
    const bot = new McBot('ver1');
    await bot.connect();
    expect(g.__createBotVersion).toBe('1.21.11');
    expect(bot.connected).toBe(true);
    bot.disconnect('test done');
  }, 10000);
});
