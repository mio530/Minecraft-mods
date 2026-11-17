import { config } from '../config.js';

/**
 * Classify a Minecraft bot connection/auth error into an actionable category
 * with a human-readable message and (when known) the protocol phase it failed
 * in. Never includes tokens or secrets — messages are static templates plus a
 * sanitized tail of the raw text.
 */
export type BotErrorCategory =
  | 'network_server' // can't reach the Minecraft server/Velocity proxy
  | 'handshake' // TCP connected but the Minecraft protocol handshake never completed
  | 'mojang_session' // can't reach/complete sessionserver.mojang.com join
  | 'invalid_auth' // Microsoft/Minecraft token invalid, expired, or no game ownership
  | 'kicked' // server actively kicked (whitelist / online-mode / plugin)
  | 'disconnect' // connection ended by the server/proxy before spawn, without a kick reason
  | 'protocol_error' // packet (de)serialization / protocol mismatch during the session
  | 'timeout' // handshake/login stalled, no response in time
  | 'unknown';

/** Minecraft protocol phase, mapped from node-minecraft-protocol client state. */
export type ProtocolPhase = 'handshaking' | 'status' | 'login' | 'configuration' | 'play' | 'unknown';

export interface ClassifiedError {
  category: BotErrorCategory;
  message: string;
  /** true if retrying (same credentials) might succeed later. */
  retryable: boolean;
  /** protocol phase the failure happened in, when reliably known. */
  phase: ProtocolPhase;
}

/** Strip token-like strings so classified errors never leak secrets. */
export function sanitize(text: string): string {
  return text
    .replace(/eyJ[\w-]{6,}\.[\w-]+\.[\w-]+/g, '[redacted]') // JWT-shaped
    .replace(/[A-Za-z0-9_-]{40,}/g, '[redacted]') // long opaque tokens
    .slice(0, 300);
}

export function normalizePhase(state: unknown): ProtocolPhase {
  const s = String(state ?? '').toLowerCase();
  if (s === 'handshaking' || s === 'status' || s === 'login' || s === 'configuration' || s === 'play') return s;
  return 'unknown';
}

/** Turn a Minecraft chat component (string, JSON string, or object tree) into
 *  plain, sanitized text — used for kick/disconnect reasons. */
export function chatToText(reason: unknown): string {
  const visit = (node: any, depth = 0): string => {
    if (node == null || depth > 12) return '';
    if (typeof node === 'string') return node;
    if (Array.isArray(node)) return node.map((n) => visit(n, depth + 1)).join('');
    if (typeof node === 'object') {
      let out = '';
      if (typeof node.text === 'string') out += node.text;
      if (typeof node.translate === 'string' && !node.text) out += node.translate;
      if (Array.isArray(node.extra)) out += node.extra.map((n: any) => visit(n, depth + 1)).join('');
      if (Array.isArray(node.with)) out += ' ' + node.with.map((n: any) => visit(n, depth + 1)).join(' ');
      return out;
    }
    return String(node);
  };
  let text: string;
  if (typeof reason === 'string') {
    try {
      text = visit(JSON.parse(reason));
    } catch {
      text = reason;
    }
  } else {
    text = visit(reason);
  }
  text = text.replace(/\s+/g, ' ').trim();
  return sanitize(text || (typeof reason === 'string' ? reason : JSON.stringify(reason))) || 'ohne Grund';
}

export interface ClassifyOpts {
  kickReason?: unknown;
  phase?: ProtocolPhase;
  /** true when the error ended the connection before spawn without a kick. */
  disconnect?: boolean;
}

export function classifyBotError(err: unknown, opts: ClassifyOpts = {}): ClassifiedError {
  const phase: ProtocolPhase = opts.phase ?? 'unknown';
  const target = `${config.MC_HOST}:${config.MC_PORT}`;
  const phaseNote = phase !== 'unknown' ? ` (Phase: ${phase})` : '';

  if (opts.kickReason !== undefined) {
    return {
      category: 'kicked',
      message: `Vom Server abgewiesen${phaseNote}: ${chatToText(opts.kickReason)}. Prüfe Whitelist/Ban, online-mode und Velocity/ViaVersion-Konfiguration.`,
      retryable: false,
      phase,
    };
  }

  const raw = err instanceof Error ? err.message : String(err ?? '');
  const code = (err as any)?.code ?? (err as any)?.cause?.code ?? '';
  const hay = `${raw} ${code}`.toLowerCase();

  // Mojang session join — a network/egress or Mojang issue.
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
        'Meist: der Container kann keine ausgehenden HTTPS-Verbindungen zu Mojang/Microsoft herstellen, oder Mojang-Störung. ' +
        `Details: ${sanitize(raw)}`,
      retryable: true,
      phase,
    };
  }

  // Can't reach the Minecraft server / Velocity proxy.
  if (
    hay.includes('econnrefused') ||
    hay.includes('etimedout') ||
    hay.includes('ehostunreach') ||
    hay.includes('enetunreach') ||
    hay.includes('enotfound')
  ) {
    return {
      category: 'network_server',
      message: `Minecraft-Server/Proxy ${target} nicht erreichbar (${code || 'Netzwerkfehler'}). Läuft der Server und stimmen MC_HOST/MC_PORT?`,
      retryable: true,
      phase,
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
        `Bitte im Dashboard neu anmelden. Details: ${sanitize(raw)}`,
      retryable: false,
      phase,
    };
  }

  // Packet (de)serialization / protocol mismatch during the session.
  if (
    hay.includes('partialreaderror') ||
    hay.includes('deserializ') ||
    hay.includes('serializ') ||
    hay.includes('read error') ||
    hay.includes('unknown packet') ||
    hay.includes('missing packet') ||
    hay.includes('chunk size') ||
    hay.includes('protodef') ||
    hay.includes('sizeof') ||
    hay.includes('format_error')
  ) {
    return {
      category: 'protocol_error',
      message:
        `Protokollfehler beim Verarbeiten eines Pakets${phaseNote}. Das deutet auf eine Client/Server-Protokoll-Inkompatibilität hin ` +
        `(z. B. ViaVersion-Übersetzung oder eine mineflayer/minecraft-data-Lücke für diese Version). Details: ${sanitize(raw)}`,
      retryable: false,
      phase,
    };
  }

  if (hay.includes('timeout') || hay.includes('handshake stalled')) {
    return {
      category: 'timeout',
      message: `Zeitüberschreitung beim Verbinden mit ${target}${phaseNote}. Server erreichbar, aber keine Antwort rechtzeitig.`,
      retryable: true,
      phase,
    };
  }

  // Connection ended before spawn without a kick reason (e.g. socketClosed).
  if (
    opts.disconnect ||
    hay.includes('socketclosed') ||
    hay.includes('econnreset') ||
    hay.includes('disconnected before spawn') ||
    hay.includes('this.client.end') ||
    hay.includes('connection reset')
  ) {
    return {
      category: 'disconnect',
      message:
        `Verbindung vom Server/Proxy getrennt${phaseNote}, bevor der Spawn erreicht wurde. ` +
        (phase === 'configuration'
          ? 'Login war erfolgreich, aber die Configuration-Phase wurde nicht abgeschlossen (häufig ViaVersion/Registry oder eine Client-Protokoll-Lücke). '
          : '') +
        `Details: ${sanitize(raw) || 'kein Grund übermittelt'}`,
      retryable: true,
      phase,
    };
  }

  return {
    category: 'unknown',
    message: `Verbindung fehlgeschlagen${phaseNote}: ${sanitize(raw) || 'unbekannter Fehler'}.`,
    retryable: true,
    phase,
  };
}

export class BotConnectionError extends Error {
  category: BotErrorCategory;
  retryable: boolean;
  phase: ProtocolPhase;
  constructor(classified: ClassifiedError) {
    super(classified.message);
    this.name = 'BotConnectionError';
    this.category = classified.category;
    this.retryable = classified.retryable;
    this.phase = classified.phase;
  }
}
