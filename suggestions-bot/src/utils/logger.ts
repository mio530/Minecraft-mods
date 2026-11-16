type Level = 'debug' | 'info' | 'warn' | 'error';

const ORDER: Record<Level, number> = { debug: 0, info: 1, warn: 2, error: 3 };

let current: Level = 'info';

/** Patterns that must never appear in logs (tokens/keys). */
const SECRET_KEYS = ['DISCORD_TOKEN', 'AI_API_KEY'];

function redact(input: unknown): unknown {
  if (typeof input !== 'string') return input;
  let out = input;
  for (const key of SECRET_KEYS) {
    const val = process.env[key];
    if (val && val.length > 6) {
      out = out.split(val).join('***REDACTED***');
    }
  }
  // Redact typical bearer tokens / long secrets just in case.
  out = out.replace(/(Bearer\s+)[A-Za-z0-9._-]{12,}/gi, '$1***REDACTED***');
  return out;
}

function emit(level: Level, args: unknown[]): void {
  if (ORDER[level] < ORDER[current]) return;
  const ts = new Date().toISOString();
  const safe = args.map(redact);
  const prefix = `[${ts}] [${level.toUpperCase()}]`;
  if (level === 'error') console.error(prefix, ...safe);
  else if (level === 'warn') console.warn(prefix, ...safe);
  else console.log(prefix, ...safe);
}

export const logger = {
  setLevel(level: Level) {
    current = level;
  },
  debug: (...a: unknown[]) => emit('debug', a),
  info: (...a: unknown[]) => emit('info', a),
  warn: (...a: unknown[]) => emit('warn', a),
  error: (...a: unknown[]) => emit('error', a),
};
