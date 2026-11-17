import net from 'node:net';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * Real protocol-level tests (no mineflayer mock):
 *  - a bare TCP connect that never completes the Minecraft handshake is
 *    classified as 'handshake' (the reported "Handshake timeout" case),
 *    distinct from an unreachable server;
 *  - the Minecraft status ping completes against a real 1.21.11 server,
 *    proving mineflayer/minecraft-protocol handshake compatibility with
 *    Minecraft 1.21.11.
 */

const SILENT_PORT = 25681;
process.env.MC_AUTH = 'offline';
process.env.MC_HOST = '127.0.0.1';
process.env.MC_PORT = String(SILENT_PORT);
process.env.CONNECT_TIMEOUT_MS = '1200';
process.env.DB_PATH = join(mkdtempSync(join(tmpdir(), 'ryze-hs-')), 'a.db');
process.env.JWT_SECRET = 'handshake_test_secret_123456';
process.env.MCP_TOKEN = 'handshake-mcp-token';
process.env.DASHBOARD_PASSWORD = 'pw123456';

let silent: net.Server;
const sockets: net.Socket[] = [];

beforeAll(async () => {
  // A server that accepts TCP but never speaks the Minecraft protocol.
  silent = net.createServer((sock) => {
    sockets.push(sock);
    sock.on('error', () => {});
  });
  await new Promise<void>((resolve) => silent.listen(SILENT_PORT, '127.0.0.1', resolve));
});

afterAll(async () => {
  for (const s of sockets) {
    try {
      s.destroy();
    } catch {
      /* ignore */
    }
  }
  await new Promise<void>((resolve) => silent.close(() => resolve()));
});

describe('handshake vs TCP', () => {
  it('classifies a TCP-connected-but-no-handshake as "handshake" (not unreachable/auth)', async () => {
    const { McBot } = await import('../mc/bot.js');
    const { BotConnectionError } = await import('../mc/errors.js');
    const bot = new McBot('hs1');
    const err = await bot.connect().then(
      () => null,
      (e) => e,
    );
    expect(err).toBeInstanceOf(BotConnectionError);
    expect(bot.errorDetail?.category).toBe('handshake');
    expect(bot.errorDetail?.message).toMatch(/Handshake|Velocity|Version/i);
    expect(bot.connected).toBe(false);
  }, 15000);
});

describe('Minecraft 1.21.11 protocol compatibility (status ping)', () => {
  it('completes a status ping against a real 1.21.11 server', async () => {
    const mc: any = await import('minecraft-protocol');
    const PORT = 25682;
    const server = mc.createServer({ 'online-mode': false, port: PORT, host: '127.0.0.1', version: '1.21.11', motd: 'ryze-test' });
    await new Promise<void>((resolve) => server.on('listening', () => resolve()));
    try {
      const { mcPingProbe } = await import('../mc/diagnostics.js');
      const r = await mcPingProbe('127.0.0.1', PORT);
      expect(r.ok).toBe(true);
      expect(r.version).toBe('1.21.11');
      expect(r.protocol).toBe(774);
    } finally {
      server.close();
    }
  }, 20000);

  it('reports a failed ping against a dead port (no handshake)', async () => {
    const { mcPingProbe } = await import('../mc/diagnostics.js');
    const r = await mcPingProbe('127.0.0.1', 25699, 1500);
    expect(r.ok).toBe(false);
    expect(r.version).toBeNull();
  }, 10000);
});
