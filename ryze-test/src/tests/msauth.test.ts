import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { existsSync, mkdtempSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * Full Microsoft device-code flow through the real HTTP API, with Microsoft's
 * side replaced by a fake prismarine-auth Authflow (no network, no real
 * account). Covers: the iPad bug (POST with JSON content-type and empty body),
 * code delivery, idempotency, polling to completion, persistence, token
 * secrecy, error surfacing with redaction, and auth guard.
 */

const SECRET = 'SECRET_ACCESS_TOKEN_XYZ_0123456789';

vi.mock('prismarine-auth', async () => {
  const fs = await import('node:fs');
  const path = await import('node:path');
  const g = globalThis as any;
  class Authflow {
    cache: string;
    cb: (r: any) => void;
    constructor(_user: string, cache: string, _opts: any, cb: (r: any) => void) {
      this.cache = cache;
      this.cb = cb;
      g.__flowCount = (g.__flowCount ?? 0) + 1;
      g.__lastOpts = _opts;
    }
    async getMinecraftJavaToken() {
      if (g.__authMode === 'fail') {
        throw new Error('XSTS failure for token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.c2lnbmF0dXJlLXZhbHVl');
      }
      this.cb({ user_code: 'RYZE-1234', verification_uri: 'https://www.microsoft.com/link', expires_in: 900, message: 'x' });
      await new Promise<void>((resolve) => (g.__approve = resolve));
      fs.writeFileSync(path.join(this.cache, 'abc123_mca-cache.json'), JSON.stringify({ mca: { access_token: SECRET } }));
      return { token: SECRET, profile: { name: 'RyzeTester' } };
    }
  }
  const Titles = { MinecraftNintendoSwitch: '00000000441cc96b', MinecraftJava: 'java-title' };
  return { default: { Authflow, Titles }, Authflow, Titles };
});

const AUTH_DIR = join(mkdtempSync(join(tmpdir(), 'ryze-auth-')), '.auth-cache');
process.env.AUTH_CACHE_DIR = AUTH_DIR;
process.env.DB_PATH = join(mkdtempSync(join(tmpdir(), 'ryze-msauth-db-')), 'a.db');
process.env.MC_AUTH = 'microsoft';
process.env.PORT = '25650';
process.env.HOST = '127.0.0.1';
process.env.JWT_SECRET = 'msauth_test_secret_123456';
process.env.MCP_TOKEN = 'msauth-mcp-token';
process.env.DASHBOARD_PASSWORD = 'pw123456';

const g = globalThis as any;
let srv: Awaited<ReturnType<typeof import('../index.js').startServer>>;
let base = '';
let owner = '';

async function call(path: string, init: RequestInit = {}) {
  const res = await fetch(base + path, init);
  const text = await res.text();
  return { status: res.status, text, json: text ? JSON.parse(text) : null };
}
const authH = () => ({ authorization: 'Bearer ' + owner });
async function pollStatus(pred: (s: any) => boolean, ms = 3000) {
  const end = Date.now() + ms;
  for (;;) {
    const r = await call('/api/auth/status', { headers: authH() });
    if (pred(r.json)) return r;
    if (Date.now() > end) throw new Error('timeout; last=' + r.text);
    await new Promise((x) => setTimeout(x, 30));
  }
}

beforeAll(async () => {
  const { startServer } = await import('../index.js');
  srv = await startServer();
  base = srv.url;
  const r = await call('/api/login', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ password: 'pw123456' }) });
  owner = r.json.token;
}, 20000);

afterAll(async () => {
  await srv?.close();
});

describe('Microsoft device-code flow (HTTP API)', () => {
  it('requires the dashboard owner session', async () => {
    expect((await call('/api/auth/login', { method: 'POST' })).status).toBe(401);
    expect((await call('/api/auth/status')).status).toBe(401);
  });

  it('starts unauthenticated with no pending code', async () => {
    const r = await call('/api/auth/status', { headers: authH() });
    expect(r.json).toMatchObject({ authenticated: false, inProgress: false, pending: null, mode: 'microsoft' });
  });

  it('does NOT treat empty `{}` cache files (created at flow start) as authenticated', async () => {
    const fs = await import('node:fs');
    fs.mkdirSync(AUTH_DIR, { recursive: true });
    fs.writeFileSync(join(AUTH_DIR, 'empty_mca-cache.json'), '{}');
    const r = await call('/api/auth/status', { headers: authH() });
    expect(r.json.authenticated).toBe(false);
  });

  it('accepts the exact iPad request: POST with JSON content-type and EMPTY body', async () => {
    const r = await call('/api/auth/login', { method: 'POST', headers: { ...authH(), 'content-type': 'application/json' } });
    expect(r.status).toBe(200);
    expect(r.json.started).toBe(true);
  });

  it('delivers the device code + verification URL (and a prefilled link)', async () => {
    const r = await pollStatus((s) => !!s.pending);
    expect(r.json.inProgress).toBe(true);
    expect(r.json.pending.userCode).toBe('RYZE-1234');
    expect(r.json.pending.verificationUri).toBe('https://www.microsoft.com/link');
    expect(r.json.pending.directUri).toBe('https://www.microsoft.com/link?otc=RYZE-1234');
    expect(r.json.pending.expiresAt).toBeGreaterThan(Date.now());
    expect(r.text).not.toContain(SECRET);
  });

  it('uses the same flow options the bot will use (cache reuse)', async () => {
    const { botAuthOptions } = await import('../auth/microsoft.js');
    const bot = await botAuthOptions();
    expect(g.__lastOpts).toMatchObject({ flow: 'live', authTitle: '00000000441cc96b', deviceType: 'Nintendo' });
    expect(bot).toMatchObject({ auth: 'microsoft', flow: 'live', authTitle: '00000000441cc96b', deviceType: 'Nintendo', profilesFolder: AUTH_DIR });
    expect(JSON.stringify(bot)).not.toContain(SECRET);
  });

  it('is idempotent while a login is in flight (no second flow)', async () => {
    const before = g.__flowCount;
    const r = await call('/api/auth/login', { method: 'POST', headers: authH() });
    expect(r.status).toBe(200);
    expect(g.__flowCount).toBe(before);
  });

  it('completes after the user approves, persists tokens on disk, never returns them', async () => {
    g.__approve();
    const r = await pollStatus((s) => s.authenticated === true);
    expect(r.json).toMatchObject({ authenticated: true, inProgress: false, pending: null, username: 'RyzeTester', error: null });
    expect(r.text).not.toContain(SECRET);
    expect(existsSync(AUTH_DIR)).toBe(true);
    expect(readdirSync(AUTH_DIR).some((f) => f.endsWith('mca-cache.json'))).toBe(true);
  });

  it('surfaces failures in status with token-like strings redacted', async () => {
    await call('/api/auth/logout', { method: 'POST', headers: authH() });
    g.__authMode = 'fail';
    await call('/api/auth/login', { method: 'POST', headers: authH() });
    const r = await pollStatus((s) => !!s.error && !s.inProgress);
    expect(r.json.authenticated).toBe(false);
    expect(r.json.error).toContain('XSTS failure');
    expect(r.json.error).not.toContain('eyJhbGci');
    expect(r.json.error).toContain('[redacted]');
    g.__authMode = undefined;
  });

  it('rejects malformed JSON bodies with 400 instead of crashing', async () => {
    const r = await call('/api/login', { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{not json' });
    expect(r.status).toBe(400);
  });

  it('explains network failures in plain language', async () => {
    const { describeAuthError } = await import('../auth/microsoft.js');
    const e = Object.assign(new Error('fetch failed'), { cause: { code: 'ENOTFOUND' } });
    expect(describeAuthError(e)).toContain('Microsoft-Server nicht erreichbar (ENOTFOUND)');
  });
});
