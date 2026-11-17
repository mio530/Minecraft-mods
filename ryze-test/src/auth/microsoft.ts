import { existsSync, mkdirSync, rmSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { config } from '../config.js';
import { logger } from '../logger.js';

/**
 * Official Microsoft OAuth 2.0 **Device Code Flow** (via prismarine-auth).
 *
 * The owner authenticates on their OWN device at microsoft.com/link by typing
 * a short code. This process never sees the Microsoft password; it only caches
 * the resulting OAuth/Minecraft tokens under AUTH_CACHE_DIR (persistent volume
 * on Pterodactyl: /home/container/.auth-cache). Tokens are NEVER returned by
 * the API or logged — only the public device code + verification URL are.
 *
 * The dashboard login and the mineflayer bot use IDENTICAL flow options and
 * cache identifier, so the bot silently reuses the cached identity.
 */

export interface DeviceCodePrompt {
  userCode: string;
  verificationUri: string;
  /** Pre-filled link (microsoft.com/link?otc=CODE) — convenient on mobile. */
  directUri: string;
  expiresAt: number;
}

export interface AuthStatus {
  authenticated: boolean;
  inProgress: boolean;
  pending: DeviceCodePrompt | null;
  username: string | null;
  error: string | null;
  mode: 'microsoft' | 'offline';
}

export const CACHE_IDENTIFIER = 'ryze-test-owner';

let pending: DeviceCodePrompt | null = null;
let authenticatedName: string | null = null;
let lastError: string | null = null;
let loginInFlight: Promise<void> | null = null;

/** prismarine-auth is CommonJS: its named exports live on `default` under ESM. */
async function loadPrismarineAuth(): Promise<{ Authflow: any; Titles: any }> {
  const mod: any = await import('prismarine-auth');
  const pa = mod.default ?? mod;
  if (!pa?.Authflow || !pa?.Titles) throw new Error('prismarine-auth not available');
  return { Authflow: pa.Authflow, Titles: pa.Titles };
}

/**
 * Flow options shared by dashboard login and the bot. These match
 * minecraft-protocol's own Microsoft defaults (the well-tested Java path), so
 * both sides read/write the same cache entries.
 */
export async function authFlowOptions(): Promise<{ flow: 'live'; authTitle: string; deviceType: string }> {
  const { Titles } = await loadPrismarineAuth();
  return { flow: 'live', authTitle: Titles.MinecraftNintendoSwitch, deviceType: 'Nintendo' };
}

function ensureCacheDir(): void {
  if (!existsSync(config.AUTH_CACHE_DIR)) mkdirSync(config.AUTH_CACHE_DIR, { recursive: true });
}

/**
 * True only if a cached **Minecraft** token exists (written after a completed
 * login). Partial caches written mid-flow do not count.
 */
export function hasCachedAuth(): boolean {
  try {
    if (!existsSync(config.AUTH_CACHE_DIR)) return false;
    // prismarine-auth creates EMPTY `{}` cache files as soon as a flow starts,
    // so a file merely existing proves nothing. Require a populated `mca`
    // entry (written only after a completed login). Content is never logged.
    return readdirSync(config.AUTH_CACHE_DIR)
      .filter((f) => f.endsWith('mca-cache.json'))
      .some((f) => {
        try {
          const j = JSON.parse(readFileSync(join(config.AUTH_CACHE_DIR, f), 'utf8'));
          return !!j && typeof j.mca === 'object' && j.mca !== null && Object.keys(j.mca).length > 0;
        } catch {
          return false;
        }
      });
  } catch {
    return false;
  }
}

/** Store a device-code prompt from prismarine-auth (snake_case or camelCase). */
export function setPendingFromResponse(resp: any): DeviceCodePrompt {
  const userCode = String(resp?.userCode ?? resp?.user_code ?? '');
  const verificationUri = String(resp?.verificationUri ?? resp?.verification_uri ?? 'https://www.microsoft.com/link');
  const expiresIn = Number(resp?.expiresIn ?? resp?.expires_in ?? 900);
  pending = {
    userCode,
    verificationUri,
    directUri: `https://www.microsoft.com/link?otc=${encodeURIComponent(userCode)}`,
    expiresAt: Date.now() + expiresIn * 1000,
  };
  // The device code is a public, short-lived pairing code (not a credential).
  logger.info({ verificationUri }, 'Microsoft device code issued');
  return pending;
}

export function status(): AuthStatus {
  if (config.MC_AUTH === 'offline') {
    return { authenticated: true, inProgress: false, pending: null, username: 'offline', error: null, mode: 'offline' };
  }
  const inProgress = loginInFlight != null;
  return {
    authenticated: authenticatedName != null || (!inProgress && hasCachedAuth()),
    inProgress,
    pending: pending && pending.expiresAt > Date.now() ? pending : null,
    username: authenticatedName,
    error: lastError,
    mode: 'microsoft',
  };
}

/**
 * Start the device-code login in the background and return immediately once
 * started. Callers poll status() for the code and completion. A login already
 * in flight is reused (idempotent).
 */
export function startLogin(): { started: boolean; alreadyAuthenticated: boolean } {
  if (config.MC_AUTH === 'offline') return { started: false, alreadyAuthenticated: true };
  if (loginInFlight) return { started: true, alreadyAuthenticated: false };
  login().catch(() => {
    /* error captured in lastError and surfaced via status() */
  });
  return { started: true, alreadyAuthenticated: false };
}

/** Full login; resolves when the owner has completed authentication. */
export async function login(): Promise<void> {
  if (config.MC_AUTH === 'offline') {
    authenticatedName = 'offline';
    return;
  }
  if (loginInFlight) return loginInFlight;

  loginInFlight = (async () => {
    ensureCacheDir();
    lastError = null;
    pending = null;
    try {
      const { Authflow } = await loadPrismarineAuth();
      const opts = await authFlowOptions();
      const flow = new Authflow(CACHE_IDENTIFIER, config.AUTH_CACHE_DIR, opts, (resp: any) => setPendingFromResponse(resp));
      const result = await flow.getMinecraftJavaToken({ fetchProfile: true });
      authenticatedName = result?.profile?.name ?? 'minecraft-user';
      logger.info({ username: authenticatedName }, 'Microsoft authentication complete');
    } catch (err) {
      lastError = describeAuthError(err);
      logger.error({ error: lastError }, 'Microsoft authentication failed');
      throw err;
    } finally {
      pending = null;
      loginInFlight = null;
    }
  })();

  return loginInFlight;
}

/** User-facing auth error: explains network failures, never leaks tokens. */
export function describeAuthError(err: unknown): string {
  const base = sanitizeError(err);
  const cause: any = (err as any)?.cause;
  const code = cause?.code ?? (err as any)?.code;
  if (/fetch failed|ENOTFOUND|ECONNREFUSED|ETIMEDOUT|EAI_AGAIN|ECONNRESET/i.test(base + ' ' + (code ?? ''))) {
    return `Microsoft-Server nicht erreichbar (${code ?? 'Netzwerk'}). Prüfe die Internetverbindung des Servers und versuche es erneut.`;
  }
  return base;
}

/** Keep error text useful but never leak tokens that may appear in messages. */
export function sanitizeError(err: unknown): string {
  const msg = err instanceof Error ? err.message : String(err);
  return msg.replace(/(eyJ[\w-]{10,}\.[\w-]+\.[\w-]+)|([A-Za-z0-9_-]{40,})/g, '[redacted]').slice(0, 300);
}

/** Removes cached tokens (owner logout). */
export function logout(): void {
  try {
    if (existsSync(config.AUTH_CACHE_DIR)) rmSync(config.AUTH_CACHE_DIR, { recursive: true, force: true });
  } catch (err) {
    logger.warn({ error: sanitizeError(err) }, 'logout cache clear failed');
  }
  authenticatedName = null;
  pending = null;
  lastError = null;
}

/** Options mineflayer needs to reuse the cached Microsoft identity. */
export async function botAuthOptions(): Promise<Record<string, unknown>> {
  if (config.MC_AUTH === 'offline') {
    return { auth: 'offline', username: 'RyzeTestBot' };
  }
  return {
    auth: 'microsoft',
    username: CACHE_IDENTIFIER,
    profilesFolder: config.AUTH_CACHE_DIR,
    ...(await authFlowOptions()),
    // If the cache is ever invalid, surface the new code in the dashboard.
    onMsaCode: (resp: any) => setPendingFromResponse(resp),
  };
}

/** TEST ONLY: reset module state. */
export function __resetForTest(): void {
  pending = null;
  authenticatedName = null;
  lastError = null;
  loginInFlight = null;
}
