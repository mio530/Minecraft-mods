import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { createRequire } from 'node:module';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * Browser end-to-end test of the dashboard "Login starten" flow.
 * Engine: Chromium with Playwright's iPad Pro 11 emulation (viewport, touch,
 * iPad user agent). navigator.clipboard is removed to mimic Safari on a
 * plain-HTTP (non-secure) origin. WebKit itself is not installed here, so this
 * is NOT a real Safari run. Microsoft's side is a fake Authflow.
 */

vi.mock('prismarine-auth', async () => {
  const fs = await import('node:fs');
  const path = await import('node:path');
  const g = globalThis as any;
  class Authflow {
    constructor(_u: string, public cache: string, _o: any, public cb: (r: any) => void) {}
    async getMinecraftJavaToken() {
      this.cb({ user_code: 'RYZE-1234', verification_uri: 'https://www.microsoft.com/link', expires_in: 900 });
      await new Promise<void>((resolve) => (g.__approve = resolve));
      fs.writeFileSync(path.join(this.cache, 'x_mca-cache.json'), '{"mca":{"access_token":"SECRET_E2E"}}');
      return { token: 'SECRET_E2E', profile: { name: 'RyzeTester' } };
    }
  }
  const Titles = { MinecraftNintendoSwitch: '00000000441cc96b' };
  return { default: { Authflow, Titles }, Authflow, Titles };
});

process.env.AUTH_CACHE_DIR = join(mkdtempSync(join(tmpdir(), 'ryze-e2e-auth-')), '.auth-cache');
process.env.DB_PATH = join(mkdtempSync(join(tmpdir(), 'ryze-e2e-ui-')), 'a.db');
process.env.MC_AUTH = 'microsoft';
process.env.PORT = '25651';
process.env.HOST = '127.0.0.1';
process.env.JWT_SECRET = 'e2e_ui_secret_1234567890';
process.env.MCP_TOKEN = 'e2e-ui-mcp-token';
process.env.DASHBOARD_PASSWORD = 'pw123456';

let pw: any = null;
try {
  pw = createRequire(import.meta.url)('playwright');
} catch {
  pw = null;
}

const g = globalThis as any;
let srv: any;
let browser: any;
let page: any;
const pageErrors: string[] = [];

describe.skipIf(!pw)('dashboard Microsoft login (browser, iPad emulation)', () => {
  beforeAll(async () => {
    const { startServer } = await import('../index.js');
    srv = await startServer();
    browser = await pw.chromium.launch();
    const ctx = await browser.newContext({ ...pw.devices['iPad Pro 11'] });
    // Mimic Safari on http:// — no async Clipboard API in a non-secure context.
    await ctx.addInitScript(() => {
      Object.defineProperty(Navigator.prototype, 'clipboard', { get: () => undefined, configurable: true });
    });
    page = await ctx.newPage();
    page.on('pageerror', (e: Error) => pageErrors.push(e.message));
    await page.goto(srv.url, { waitUntil: 'networkidle' });
    await page.fill('#password', 'pw123456');
    await page.tap('#loginForm button[type=submit]');
    await page.waitForSelector('#app:not([hidden])');
    await page.waitForSelector('#msAuth:not([hidden])');
  }, 30000);

  afterAll(async () => {
    await browser?.close();
    await srv?.close();
  });

  it('tapping "Login starten" shows the device code with links', async () => {
    await page.tap('#btnMsLogin');
    await page.waitForFunction(() => (globalThis as any).document.getElementById('msCode')?.textContent === 'RYZE-1234', null, { timeout: 8000 });
    expect(await page.isVisible('#msCodeBox')).toBe(true);
    expect(await page.textContent('#btnMsLogin')).toBe('Warte auf Anmeldung…');
    expect(await page.isDisabled('#btnMsLogin')).toBe(true);
    expect(await page.getAttribute('#msDirect', 'href')).toBe('https://www.microsoft.com/link?otc=RYZE-1234');
    expect(await page.getAttribute('#msLink', 'href')).toBe('https://www.microsoft.com/link');
    expect(await page.content()).not.toContain('SECRET_E2E');
    if (process.env.E2E_SHOT_DIR) {
      await page.locator('#msAuth').screenshot({ path: join(process.env.E2E_SHOT_DIR, 'ms-device-code-ipad.png') });
    }
  }, 15000);

  it('copy button works without navigator.clipboard (shows feedback)', async () => {
    await page.tap('#btnCopyCode');
    await page.waitForSelector('#toast:not([hidden])');
    expect(await page.textContent('#toast')).toMatch(/Code/);
  });

  it('banner disappears automatically after the user completes sign-in (polling)', async () => {
    g.__approve();
    await page.waitForSelector('#msAuth', { state: 'hidden', timeout: 8000 });
    expect(await page.content()).not.toContain('SECRET_E2E');
  }, 15000);

  it('shows a visible error and re-enables the button when the start request fails', async () => {
    // Back to logged-out Microsoft state, then make the start call fail.
    await page.evaluate(async () => {
      await fetch('/api/auth/logout', { method: 'POST', headers: { authorization: 'Bearer ' + localStorage.getItem('ryze_token') } });
    });
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForSelector('#msAuth:not([hidden])');
    await page.route('**/api/auth/login', (route: any) =>
      route.fulfill({ status: 502, contentType: 'application/json', body: JSON.stringify({ error: 'upstream unavailable' }) }),
    );
    await page.tap('#btnMsLogin');
    await page.waitForSelector('#msError:not([hidden])');
    expect(await page.textContent('#msError')).toContain('upstream unavailable');
    expect(await page.isDisabled('#btnMsLogin')).toBe(false);
    expect(await page.textContent('#btnMsLogin')).toBe('Erneut versuchen');
    expect(await page.isVisible('#toast')).toBe(true);
  }, 15000);

  it('raised no uncaught JavaScript errors', () => {
    expect(pageErrors).toEqual([]);
  });
});
