import { existsSync, mkdirSync, rmSync, readdirSync } from 'node:fs';
import { config } from '../config.js';
import { logger } from '../logger.js';

/**
 * Official Microsoft OAuth 2.0 **Device Code Flow**.
 *
 * The owner authenticates on their OWN device at https://microsoft.com/link
 * by typing a short code. This process:
 *   - never sees or stores the Microsoft password,
 *   - caches only the resulting OAuth tokens (encrypted at rest by
 *     prismarine-auth) under AUTH_CACHE_DIR,
 *   - produces a Minecraft Java session token that mineflayer reuses silently.
 *
 * prismarine-auth is imported dynamically so the module loads even before the
 * dependency is installed (useful for typecheck / partial deploys).
 */

export interface DeviceCodePrompt {
  userCode: string;
  verificationUri: string;
  message: string;
  expiresAt: number;
}

export interface AuthStatus {
  authenticated: boolean;
  pending: DeviceCodePrompt | null;
  username: string | null;
  error: string | null;
}

const CACHE_IDENTIFIER = 'ryze-test-owner';

let pending: DeviceCodePrompt | null = null;
let authenticatedName: string | null = null;
let lastError: string | null = null;
let loginInFlight: Promise<void> | null = null;

function ensureCacheDir(): void {
  if (!existsSync(config.AUTH_CACHE_DIR)) mkdirSync(config.AUTH_CACHE_DIR, { recursive: true });
}

/** True if a token cache already exists (owner previously logged in). */
export function hasCachedAuth(): boolean {
  try {
    if (!existsSync(config.AUTH_CACHE_DIR)) return false;
    return readdirSync(config.AUTH_CACHE_DIR).some((f) => f.includes('cache') || f.endsWith('.json'));
  } catch {
    return false;
  }
}

export function status(): AuthStatus {
  return {
    authenticated: authenticatedName != null || hasCachedAuth(),
    pending,
    username: authenticatedName,
    error: lastError,
  };
}

/**
 * Starts (or resumes) the device-code login. Resolves once the owner has
 * completed authentication on their device. Safe to call repeatedly: a login
 * already in flight is reused.
 */
export async function login(): Promise<void> {
  if (config.MC_AUTH === 'offline') {
    authenticatedName = 'offline-test';
    return;
  }
  if (loginInFlight) return loginInFlight;

  loginInFlight = (async () => {
    ensureCacheDir();
    lastError = null;
    try {
      const { Authflow, Titles } = await import('prismarine-auth');
      const flow = new Authflow(
        CACHE_IDENTIFIER,
        config.AUTH_CACHE_DIR,
        { flow: 'live', authTitle: Titles.MinecraftJava, deviceType: 'Win32' },
        (resp: any) => {
          // prismarine-auth returns snake_case fields (user_code, verification_uri).
          const userCode = resp.userCode ?? resp.user_code;
          const verificationUri = resp.verificationUri ?? resp.verification_uri;
          const expiresIn = resp.expiresIn ?? resp.expires_in ?? 900;
          pending = {
            userCode,
            verificationUri,
            message: resp.message ?? `Go to ${verificationUri} and enter ${userCode}`,
            expiresAt: Date.now() + expiresIn * 1000,
          };
          logger.info({ userCode, uri: verificationUri }, 'MS device code issued');
        },
      );
      const token = await flow.getMinecraftJavaToken({ fetchProfile: true });
      authenticatedName = token?.profile?.name ?? 'minecraft-user';
      logger.info({ username: authenticatedName }, 'Microsoft authentication complete');
    } catch (err) {
      lastError = err instanceof Error ? err.message : String(err);
      logger.error({ err }, 'Microsoft authentication failed');
      throw err;
    } finally {
      pending = null;
      loginInFlight = null;
    }
  })();

  return loginInFlight;
}

/** Removes cached tokens (owner logout). */
export function logout(): void {
  try {
    if (existsSync(config.AUTH_CACHE_DIR)) rmSync(config.AUTH_CACHE_DIR, { recursive: true, force: true });
  } catch (err) {
    logger.warn({ err }, 'logout cache clear failed');
  }
  authenticatedName = null;
  pending = null;
}

/** Values mineflayer needs to reuse the cached Microsoft identity. */
export function botAuthOptions(): { auth: 'microsoft' | 'offline'; username: string; profilesFolder: string } {
  if (config.MC_AUTH === 'offline') {
    return { auth: 'offline', username: 'RyzeTestBot', profilesFolder: config.AUTH_CACHE_DIR };
  }
  return { auth: 'microsoft', username: CACHE_IDENTIFIER, profilesFolder: config.AUTH_CACHE_DIR };
}
