import 'dotenv/config';
import { z } from 'zod';

/**
 * Central configuration. Every secret comes from an environment variable so
 * nothing sensitive is ever hard-coded, shipped to the browser, or logged.
 */
const Env = z.object({
  // --- HTTP / dashboard ---
  PORT: z.coerce.number().int().positive().default(25571),
  HOST: z.string().default('0.0.0.0'),
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

export type AppConfig = z.infer<typeof Env>;

function load(): AppConfig {
  const parsed = Env.safeParse(process.env);
  if (!parsed.success) {
    // eslint-disable-next-line no-console
    console.error('Invalid configuration:', parsed.error.flatten().fieldErrors);
    process.exit(1);
  }
  return parsed.data;
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
