import { config } from '../config.js';

/**
 * Classify a Minecraft bot connection/auth error into an actionable category
 * with a human-readable message. Never includes tokens or secrets — messages
 * are static templates plus a sanitized tail of the raw text.
 */
export type BotErrorCategory =
  | 'network_server' // can't reach the Minecraft server/Velocity proxy
  | 'handshake' // TCP connected but the Minecraft protocol handshake never completed
  | 'mojang_session' // can't reach/complete sessionserver.mojang.com join
  | 'invalid_auth' // Microsoft/Minecraft token invalid, expired, or no game ownership
  | 'kicked' // server actively kicked (whitelist / online-mode / plugin)
  | 'timeout' // handshake stalled / connect timeout
  | 'unknown';

export interface ClassifiedError {
  category: BotErrorCategory;
  message: string;
  /** true if retrying (same credentials) might succeed later. */
  retryable: boolean;
}

/** Strip token-like strings so classified errors never leak secrets. */
export function sanitize(text: string): string {
  return text
    .replace(/eyJ[\w-]{6,}\.[\w-]+\.[\w-]+/g, '[redacted]') // JWT-shaped
    .replace(/[A-Za-z0-9_-]{40,}/g, '[redacted]') // long opaque tokens
    .slice(0, 300);
}

export function classifyBotError(err: unknown, kickReason?: unknown): ClassifiedError {
  if (kickReason !== undefined) {
    const reason = typeof kickReason === 'string' ? kickReason : JSON.stringify(kickReason);
    return {
      category: 'kicked',
      message: `Vom Server abgewiesen: ${sanitize(reason)}. Prüfe, ob der Test-Account gewhitelistet und der Server online-mode/Velocity korrekt konfiguriert ist.`,
      retryable: false,
    };
  }

  const raw = err instanceof Error ? err.message : String(err ?? '');
  const code = (err as any)?.code ?? (err as any)?.cause?.code ?? '';
  const hay = `${raw} ${code}`.toLowerCase();
  const target = `${config.MC_HOST}:${config.MC_PORT}`;

  // Mojang session join (the reported failure) — a network/egress or Mojang issue.
  if (
    hay.includes('sessionserver.mojang.com') ||
    hay.includes('session/minecraft/join') ||
    hay.includes('err_stream_premature_close') ||
    hay.includes('invalid response body') ||
    hay.includes('premature close')
  ) {
    return {
      category: 'mojang_session',
      message:
        'Mojang-Sessionserver konnte nicht erreicht/abgeschlossen werden (sessionserver.mojang.com). ' +
        'Ursache ist meist, dass der Pterodactyl-Container keine ausgehenden HTTPS-Verbindungen zu Mojang/Microsoft herstellen kann (Firewall/Egress), oder eine Mojang-Störung. ' +
        `Details: ${sanitize(raw)}`,
      retryable: true,
    };
  }

  // Can't reach the Minecraft server / Velocity proxy.
  if (
    hay.includes('econnrefused') ||
    hay.includes('etimedout') ||
    hay.includes('ehostunreach') ||
    hay.includes('enetunreach') ||
    hay.includes('enotfound') ||
    hay.includes('econnreset')
  ) {
    return {
      category: 'network_server',
      message: `Minecraft-Server/Proxy ${target} nicht erreichbar (${code || 'Netzwerkfehler'}). Läuft der Server und stimmen MC_HOST/MC_PORT?`,
      retryable: true,
    };
  }

  // Invalid / expired authentication, or account does not own the game.
  if (
    hay.includes('401') ||
    hay.includes('403') ||
    hay.includes('unauthorized') ||
    hay.includes('invalid token') ||
    hay.includes('invalidtoken') ||
    hay.includes('xsts') ||
    hay.includes('does not own') ||
    hay.includes('not own minecraft') ||
    hay.includes('forbiddenoperation')
  ) {
    return {
      category: 'invalid_auth',
      message:
        'Microsoft/Minecraft-Anmeldung ungültig oder abgelaufen (oder der Account besitzt Minecraft nicht). ' +
        'Bitte im Dashboard neu mit dem Test-Account anmelden. ' +
        `Details: ${sanitize(raw)}`,
      retryable: false,
    };
  }

  if (hay.includes('timeout') || hay.includes('handshake stalled')) {
    return {
      category: 'timeout',
      message: `Zeitüberschreitung beim Verbinden mit ${target} (Handshake blieb hängen). Server erreichbar, aber keine Antwort rechtzeitig.`,
      retryable: true,
    };
  }

  return {
    category: 'unknown',
    message: `Verbindung fehlgeschlagen: ${sanitize(raw) || 'unbekannter Fehler'}.`,
    retryable: true,
  };
}

export class BotConnectionError extends Error {
  category: BotErrorCategory;
  retryable: boolean;
  constructor(classified: ClassifiedError) {
    super(classified.message);
    this.name = 'BotConnectionError';
    this.category = classified.category;
    this.retryable = classified.retryable;
  }
}
