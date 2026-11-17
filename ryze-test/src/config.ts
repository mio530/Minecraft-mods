import 'dotenv/config';
import { z } from 'zod';
import { resolveMcVersion } from './mc/version.js';

/**
 * Central configuration. Every secret comes from an environment variable so
 * nothing sensitive is ever hard-coded, shipped to the browser, or logged.
 */
/**
 * Resolve the HTTP port. Pterodactyl injects the empty string PORT="" plus
 * SERVER_PORT=<allocation>, so an empty/whitespace/undefined PORT must fall
 * back to SERVER_PORT, and only then to the dev default. Any explicitly set
 * but invalid value (0, negative, non-numeric, out of range) is a hard error —
 * we never silently bind to port 0.
 */
export function resolvePort(portRaw?: string | null, serverPortRaw?: string | null): number {
  const parse = (val: string | null | undefined, source: string): number | null => {
    if (val === undefined || val === null) return null;
    const s = String(val).trim();
    if (s === '') return null;
    const n = Number(s);
    if (!Number.isInteger(n) || n <= 0 || n > 65535) {
      throw new Error(`Invalid ${source} "${s}" (must be an integer 1-65535)`);
    }
    return n;
  };
  const explicit = parse(portRaw, 'PORT');
  if (explicit !== null) return explicit;
  const fromServer = parse(serverPortRaw, 'SERVER_PORT');
  if (fromServer !== null) return fromServer;
  return 25571; // dev default when nothing is configured anywhere
}

/**
 * Resolve the HTTP/MCP *bind* host. Inside a Pterodactyl container the public
 * allocation IP (SERVER_IP, e.g. 203.0.113.10) is NOT a local interface, so
 * binding to it fails with EADDRNOTAVAIL. We therefore:
 *   - never read SERVER_IP (that is the public address, not a bind target),
 *   - default to 0.0.0.0 (all interfaces),
 *   - honour an explicit HOST only when it is a recognised loopback/wildcard
 *     value for local dev; anything else (including a public IP accidentally
 *     put in HOST) falls back to 0.0.0.0.
 * This is independent of MC_HOST (the Minecraft target to connect OUT to).
 */
export function resolveHost(hostRaw?: string | null): string {
  const s = (hostRaw ?? '').trim().toLowerCase();
  if (s === '') return '0.0.0.0';
  const safeLocal = new Set(['0.0.0.0', '::', '127.0.0.1', '::1', 'localhost']);
  return safeLocal.has(s) ? s : '0.0.0.0';
}

const Env = z.object({
  // --- HTTP / dashboard ---
  // NOTE: PORT and HOST are resolved separately (see resolvePort/resolveHost)
  // because Pterodactyl passes PORT as an empty string, which zod .default()
  // does not treat as "unset".
  PUBLIC_URL: z.string().default(''), // e.g. https://test.ryzesmp.org (used in docs/links)

  // --- Target Minecraft server (RyzeSMP) ---
  MC_HOST: z.string().default('203.0.113.10'),
  MC_PORT: z.coerce.number().int().positive().default(25565),
  MC_VERSION: z.string().default('1.21.11'),
  // 'microsoft' = real online-mode account (default). 'offline' = cracked/test backend only.
  MC_AUTH: z.enum(['microsoft', 'offline']).default('microsoft'),

  // --- Security ---
  // Secret used to sign dashboard JWTs. MUST be set in production.
  JWT_SECRET: z.string().min(16).default('CHANGE_ME_dev_only_secret_change_me'),
  // Dashboard login password (owner). Bcrypt-less: compared in constant time.
  DASHBOARD_PASSWORD: z.string().min(1).default('changeme'),
  // Static bearer token ChatGPT/MCP must present. MUST be set in production.
  MCP_TOKEN: z.string().min(8).default('CHANGE_ME_mcp_token'),
  // Allow the MCP client (primary AI controller, e.g. Claude Code) to start/stop
  // sessions. Dangerous server commands always remain owner-approved.
  MCP_ALLOW_SESSIONS: z.coerce.boolean().default(true),
  // Folder where MS auth tokens (NOT passwords) are cached, encrypted at rest by prismarine-auth.
  AUTH_CACHE_DIR: z.string().default('./.auth-cache'),

  // --- Behaviour / safety ---
  SESSION_TIMEOUT_MS: z.coerce.number().int().positive().default(15 * 60 * 1000),
  CONNECT_TIMEOUT_MS: z.coerce.number().int().positive().default(30_000),
  // Log incoming/outgoing login+configuration packet NAMES (never payloads).
  MC_DEBUG_PACKETS: z.coerce.boolean().default(false),
  MAX_SESSIONS: z.coerce.number().int().positive().default(1),
  // Commands/actions per minute per session (rate limiting).
  ACTION_RATE_PER_MIN: z.coerce.number().int().positive().default(60),
  // Chat/command allow policy. 'safe' blocks destructive ops unless owner-approved.
  SAFETY_MODE: z.enum(['safe', 'open']).default('safe'),
  // Comma-separated commands that always require explicit owner approval.
  DANGEROUS_COMMANDS: z.string().default('/op,/stop,/ban,/deop,/whitelist,/pardon,/kick,/gamerule,/difficulty'),

  // --- Rendering (screenshots). Optional: needs native canvas/gl on the host. ---
  VIEWER_ENABLED: z.coerce.boolean().default(false),
  VIEWER_WIDTH: z.coerce.number().int().positive().default(640),
  VIEWER_HEIGHT: z.coerce.number().int().positive().default(360),

  LOG_LEVEL: z.enum(['debug', 'info', 'warn', 'error']).default('info'),
  DB_PATH: z.string().default('./data/ryze-test.db'),
});

export type AppConfig = z.infer<typeof Env> & { PORT: number; HOST: string };

function load(): AppConfig {
  const parsed = Env.safeParse(process.env);
  if (!parsed.success) {
    // eslint-disable-next-line no-console
    console.error('Invalid configuration:', parsed.error.flatten().fieldErrors);
    process.exit(1);
  }
  let PORT: number;
  let HOST: string;
  try {
    PORT = resolvePort(process.env.PORT, process.env.SERVER_PORT);
    // Bind host: deliberately ignores SERVER_IP (the public allocation IP).
    HOST = resolveHost(process.env.HOST);
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error('Invalid configuration:', err instanceof Error ? err.message : String(err));
    process.exit(1);
  }
  const MC_VERSION = resolveMcVersion(process.env.MC_VERSION, (m) => {
    // eslint-disable-next-line no-console
    console.warn('Config:', m);
  });
  return { ...parsed.data, PORT, HOST, MC_VERSION };
}

export const config = load();

export const dangerousCommands = config.DANGEROUS_COMMANDS.split(',')
  .map((c) => c.trim().toLowerCase())
  .filter(Boolean);

/** True if the given chat/command string must be owner-approved before sending. */
export function isDangerous(message: string): boolean {
  if (config.SAFETY_MODE === 'open') return false;
  const m = message.trim().toLowerCase();
  if (!m.startsWith('/')) return false;
  const cmd = m.split(/\s+/)[0];
  return dangerousCommands.some((d) => cmd === d || cmd.startsWith(d + ' '));
}
