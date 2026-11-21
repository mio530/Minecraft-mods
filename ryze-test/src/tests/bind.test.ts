import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * Regression test for the Pterodactyl crash:
 *   EADDRNOTAVAIL: address not available 203.0.113.10:25572
 * Pterodactyl sets SERVER_IP to the PUBLIC allocation IP, which is not a local
 * interface inside the container. The HTTP/MCP listener must still bind to
 * 0.0.0.0. If it tried to bind SERVER_IP, startServer() below would throw
 * EADDRNOTAVAIL and this suite would fail at startup.
 */

// Simulate the exact Pterodactyl environment BEFORE importing config/index.
process.env.SERVER_IP = '203.0.113.10'; // public allocation IP (must be ignored for binding)
process.env.SERVER_PORT = '25572'; // allocation port
process.env.PORT = ''; // Pterodactyl passes it empty
process.env.MC_HOST = '127.0.0.1'; // Minecraft target — must stay separate
process.env.MC_PORT = '25565';
process.env.DB_PATH = join(mkdtempSync(join(tmpdir(), 'ryze-bind-')), 'audit.db');
process.env.JWT_SECRET = 'bind_test_secret_key_1234567890';
process.env.MCP_TOKEN = 'bind-test-token';
process.env.DASHBOARD_PASSWORD = 'pw123456';
process.env.MC_AUTH = 'offline';

let config: typeof import('../config.js').config;
let srv: Awaited<ReturnType<typeof import('../index.js').startServer>>;

beforeAll(async () => {
  config = (await import('../config.js')).config;
  const { startServer } = await import('../index.js');
  // If bind host were SERVER_IP (203.0.113.10) this would throw EADDRNOTAVAIL.
  srv = await startServer();
}, 20000);

afterAll(async () => {
  await srv?.close();
});

describe('listener binding under Pterodactyl SERVER_IP', () => {
  it('binds the HTTP/MCP listener to 0.0.0.0, not the public SERVER_IP', () => {
    expect(config.HOST).toBe('0.0.0.0');
  });

  it('uses the allocation port from SERVER_PORT (PORT was empty)', () => {
    expect(config.PORT).toBe(25572);
  });

  it('keeps the Minecraft target separate (MC_HOST unchanged)', () => {
    expect(config.MC_HOST).toBe('127.0.0.1');
    expect(config.MC_PORT).toBe(25565);
  });

  it('is actually reachable on the bound interface', async () => {
    const res = await fetch(`${srv.url}/healthz`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as { ok: boolean };
    expect(body.ok).toBe(true);
  });
});
