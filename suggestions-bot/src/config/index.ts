import 'dotenv/config';

export interface BotConfig {
  discord: {
    token: string;
    clientId: string;
    guildId: string;
  };
  /**
   * Optional channel IDs from the environment. These act as a fallback only;
   * the authoritative IDs are stored in the database once `/suggestion setup`
   * has run, so members never need to copy IDs manually.
   */
  channels: {
    submission: string;
    voting: string;
    log: string;
  };
  staffRoleIds: string[];
  ai: {
    provider: 'openai' | 'anthropic' | 'none';
    apiKey: string;
    model: string;
    rateLimitPerMinute: number;
    similarityThreshold: number;
  };
  voting: {
    durationDays: number;
    minimumVotes: number;
    minimumUpvotes: number;
    minimumPositiveRatio: number;
    evaluationIntervalMinutes: number;
  };
  databasePath: string;
  suggestionIdPrefix: string;
  logLevel: 'debug' | 'info' | 'warn' | 'error';
}

function req(name: string): string {
  const v = process.env[name];
  if (!v || v.trim() === '') {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return v.trim();
}

function opt(name: string, fallback: string): string {
  const v = process.env[name];
  return v && v.trim() !== '' ? v.trim() : fallback;
}

function num(name: string, fallback: number): number {
  const v = process.env[name];
  if (!v || v.trim() === '') return fallback;
  const n = Number(v);
  if (Number.isNaN(n)) throw new Error(`Environment variable ${name} must be a number`);
  return n;
}

function list(name: string): string[] {
  const v = process.env[name];
  if (!v) return [];
  return v
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

let cached: BotConfig | null = null;

/**
 * Load, validate and cache the bot configuration from environment variables.
 * Throws a descriptive error when required values are missing or invalid.
 */
export function loadConfig(): BotConfig {
  if (cached) return cached;

  const provider = opt('AI_PROVIDER', 'none').toLowerCase();
  if (!['openai', 'anthropic', 'none'].includes(provider)) {
    throw new Error(`AI_PROVIDER must be one of openai|anthropic|none (got "${provider}")`);
  }
  if (provider !== 'none' && !process.env.AI_API_KEY) {
    throw new Error(`AI_PROVIDER is "${provider}" but AI_API_KEY is not set`);
  }

  cached = {
    discord: {
      token: req('DISCORD_TOKEN'),
      clientId: req('DISCORD_CLIENT_ID'),
      guildId: req('DISCORD_GUILD_ID'),
    },
    channels: {
      submission: opt('SUBMISSION_CHANNEL_ID', ''),
      voting: opt('VOTING_CHANNEL_ID', ''),
      log: opt('LOG_CHANNEL_ID', ''),
    },
    staffRoleIds: list('STAFF_ROLE_IDS'),
    ai: {
      provider: provider as BotConfig['ai']['provider'],
      apiKey: opt('AI_API_KEY', ''),
      model: opt('AI_MODEL', 'gpt-4o-mini'),
      rateLimitPerMinute: num('AI_RATE_LIMIT_PER_MINUTE', 10),
      similarityThreshold: num('AI_SIMILARITY_THRESHOLD', 0.82),
    },
    voting: {
      durationDays: num('VOTING_DURATION_DAYS', 7),
      minimumVotes: num('MINIMUM_VOTES', 10),
      minimumUpvotes: num('MINIMUM_UPVOTES', 5),
      minimumPositiveRatio: num('MINIMUM_POSITIVE_RATIO', 60),
      evaluationIntervalMinutes: num('EVALUATION_INTERVAL_MINUTES', 15),
    },
    databasePath: opt('DATABASE_PATH', './data/suggestions.db'),
    suggestionIdPrefix: opt('SUGGESTION_ID_PREFIX', 'RS'),
    logLevel: opt('LOG_LEVEL', 'info') as BotConfig['logLevel'],
  };

  if (cached.staffRoleIds.length === 0) {
    // Not fatal, but moderation will be locked to Discord permissions only.
    // Logged by the caller.
  }

  return cached;
}

/** For tests: reset the cached config. */
export function resetConfigCache(): void {
  cached = null;
}
