import { EventEmitter } from 'node:events';
import { config } from '../config.js';
import { logger } from '../logger.js';
import { botAuthOptions } from '../auth/microsoft.js';
import { BotConnectionError, ClassifiedError, classifyBotError } from './errors.js';
import {
  ActionRequest,
  ActionResult,
  ChatLine,
  EntityLite,
  ItemLite,
  Observation,
} from './types.js';

const CHAT_BUFFER = 50;

/**
 * Wraps a single mineflayer bot: a real protocol-level Minecraft client
 * authenticated with the owner's Microsoft identity. Provides a clean
 * observe() / act() surface for both the human dashboard and the AI.
 */
export class McBot extends EventEmitter {
  readonly sessionId: string;
  // mineflayer bot instance (typed loosely; mineflayer ships its own types at runtime)
  private bot: any = null;
  private chat: ChatLine[] = [];
  private _connected = false;
  private _error: string | null = null;
  private _errorDetail: ClassifiedError | null = null;

  constructor(sessionId: string) {
    super();
    this.sessionId = sessionId;
  }

  get connected(): boolean {
    return this._connected;
  }
  get error(): string | null {
    return this._error;
  }
  get errorDetail(): ClassifiedError | null {
    return this._errorDetail;
  }
  get raw(): any {
    return this.bot;
  }

  /**
   * Attach an already-constructed bot object without opening a network
   * connection. Used ONLY by the integration test harness to drive the full
   * observe/act/service stack against a simulated protocol bot. Never called
   * in production (connect() is the real path).
   */
  attachForTest(botLike: any): void {
    this.bot = botLike;
    this.wireEvents();
    this._connected = true;
  }

  async connect(): Promise<void> {
    const mineflayer = (await import('mineflayer')).default ?? (await import('mineflayer'));
    const auth = await botAuthOptions();
    logger.info({ host: config.MC_HOST, port: config.MC_PORT, auth: auth.auth }, 'bot connecting');

    try {
      this.bot = (mineflayer as any).createBot({
        host: config.MC_HOST,
        port: config.MC_PORT,
        // config.MC_VERSION is already validated/normalised to a supported
        // version (see resolveMcVersion), so mineflayer never receives an
        // unsupported string like "auto".
        version: config.MC_VERSION,
        hideErrors: true, // we classify + surface errors ourselves (no noisy stderr)
        ...auth,
      });
    } catch (err) {
      const classified = classifyBotError(err);
      this._errorDetail = classified;
      this._error = classified.message;
      throw new BotConnectionError(classified);
    }

    this.wireEvents();

    // Track whether the raw TCP socket connected, so a timeout can distinguish
    // "server unreachable" from "TCP ok but Minecraft handshake never completed".
    let tcpConnected = false;
    try {
      this.bot._client?.on?.('connect', () => {
        tcpConnected = true;
      });
    } catch {
      /* ignore */
    }

    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => {
        cleanup();
        try {
          this.bot.end('connect timeout');
        } catch {
          /* ignore */
        }
        const classified = tcpConnected
          ? {
              category: 'handshake' as const,
              retryable: true,
              message:
                `TCP zu ${config.MC_HOST}:${config.MC_PORT} verbunden, aber der Minecraft-Handshake wurde nicht abgeschlossen (Zeitüberschreitung). ` +
                'Mögliche Ursachen: Port ist nicht der Java-Entrypoint von Velocity, Versions-/Protokoll-Inkompatibilität (MC_VERSION muss die Server-Version sein, z. B. 1.21.11), oder Velocity online-mode/forwarding. Kein Auth-Problem.',
            }
          : classifyBotError(new Error(`connection timeout: ${config.MC_HOST}:${config.MC_PORT} antwortet nicht`));
        this._errorDetail = classified;
        this._error = classified.message;
        reject(new BotConnectionError(classified));
      }, config.CONNECT_TIMEOUT_MS);
      const onSpawn = () => {
        this._connected = true;
        cleanup();
        resolve();
      };
      const onError = (err: Error) => {
        const classified = classifyBotError(err);
        this._errorDetail = classified;
        this._error = classified.message;
        cleanup();
        reject(new BotConnectionError(classified));
      };
      const onEnd = (reason: string) => {
        if (!this._connected) {
          const classified = classifyBotError(new Error(`disconnected before spawn: ${reason}`));
          this._errorDetail = classified;
          this._error = classified.message;
          cleanup();
          reject(new BotConnectionError(classified));
        }
      };
      const cleanup = () => {
        clearTimeout(timer);
        this.bot.removeListener('spawn', onSpawn);
        this.bot.removeListener('error', onError);
        this.bot.removeListener('end', onEnd);
      };
      this.bot.once('spawn', onSpawn);
      this.bot.once('error', onError);
      this.bot.once('end', onEnd);
    });
  }

  private wireEvents(): void {
    this.bot.on('messagestr', (message: string, _position: string, jsonMsg: any) => {
      const line: ChatLine = { ts: Date.now(), username: jsonMsg?.username ?? null, text: message };
      this.chat.push(line);
      if (this.chat.length > CHAT_BUFFER) this.chat.shift();
      this.emit('chat', line);
    });
    this.bot.on('kicked', (reason: any) => {
      const classified = classifyBotError(undefined, reason);
      this._errorDetail = classified;
      this._error = classified.message;
      this._connected = false;
      this.emit('botError', classified);
      this.emit('end', classified.message);
    });
    this.bot.on('end', (reason: string) => {
      this._connected = false;
      this.emit('end', reason);
    });
    // IMPORTANT: emit 'botError', never the special 'error' event. An 'error'
    // event with no listener makes Node throw and would crash the whole
    // dashboard. 'botError' is safe even with no listeners.
    this.bot.on('error', (err: Error) => {
      const classified = classifyBotError(err);
      this._errorDetail = classified;
      this._error = classified.message;
      logger.warn({ category: classified.category }, 'bot error (non-fatal)');
      this.emit('botError', classified);
    });
    this.bot.on('health', () => this.emit('state'));
    this.bot.on('move', () => this.emit('state'));
  }

  observe(): Observation {
    const b = this.bot;
    const now = Date.now();
    if (!b || !this._connected || !b.entity) {
      return {
        connected: false,
        sessionId: this.sessionId,
        serverHost: `${config.MC_HOST}:${config.MC_PORT}`,
        username: null,
        position: null,
        yaw: null,
        pitch: null,
        health: null,
        food: null,
        saturation: null,
        experience: null,
        dimension: null,
        gameMode: null,
        timeOfDay: null,
        isRaining: false,
        onGround: false,
        heldItem: null,
        nearbyEntities: [],
        nearbyPlayers: [],
        recentChat: this.chat.slice(-20),
        tookAt: now,
      };
    }

    const pos = b.entity.position;
    const entities: EntityLite[] = Object.values(b.entities ?? {})
      .filter((e: any) => e && e.id !== b.entity.id && e.position)
      .map((e: any) => ({
        id: e.id,
        name: e.name ?? e.displayName ?? 'unknown',
        type: e.type ?? 'unknown',
        username: e.username ?? null,
        distance: Number(e.position.distanceTo(pos).toFixed(2)),
        position: { x: +e.position.x.toFixed(2), y: +e.position.y.toFixed(2), z: +e.position.z.toFixed(2) },
      }))
      .sort((a, b2) => a.distance - b2.distance)
      .slice(0, 15);

    return {
      connected: true,
      sessionId: this.sessionId,
      serverHost: `${config.MC_HOST}:${config.MC_PORT}`,
      username: b.username ?? null,
      position: { x: +pos.x.toFixed(2), y: +pos.y.toFixed(2), z: +pos.z.toFixed(2) },
      yaw: +(b.entity.yaw ?? 0).toFixed(3),
      pitch: +(b.entity.pitch ?? 0).toFixed(3),
      health: b.health ?? null,
      food: b.food ?? null,
      saturation: b.foodSaturation ?? null,
      experience: b.experience?.level ?? null,
      dimension: b.game?.dimension ?? null,
      gameMode: b.game?.gameMode ?? null,
      timeOfDay: b.time?.timeOfDay ?? null,
      isRaining: !!b.isRaining,
      onGround: !!b.entity.onGround,
      heldItem: this.toItem(b.heldItem),
      nearbyEntities: entities,
      nearbyPlayers: Object.keys(b.players ?? {}).filter((n) => n !== b.username),
      recentChat: this.chat.slice(-20),
      tookAt: now,
    };
  }

  private toItem(it: any): ItemLite | null {
    if (!it) return null;
    return { slot: it.slot ?? -1, name: it.name, displayName: it.displayName ?? it.name, count: it.count ?? 1 };
  }

  inventory(): ItemLite[] {
    if (!this.bot?.inventory) return [];
    return this.bot.inventory.items().map((it: any) => this.toItem(it)!).filter(Boolean);
  }

  async sendChat(message: string): Promise<void> {
    if (!this._connected) throw new Error('not connected');
    this.bot.chat(message);
  }

  /** Executes one discrete action and returns an immediate result. */
  async act(req: ActionRequest): Promise<ActionResult> {
    if (!this._connected) return { ok: false, action: req.type, message: 'not connected' };
    const b = this.bot;
    try {
      switch (req.type) {
        case 'stopAll':
          this.clearControls();
          return { ok: true, action: req.type, message: 'all controls cleared' };

        case 'look': {
          const yaw = req.yaw ?? (req.yawDeg != null ? (req.yawDeg * Math.PI) / 180 : b.entity.yaw);
          const pitch = req.pitch ?? (req.pitchDeg != null ? (req.pitchDeg * Math.PI) / 180 : b.entity.pitch);
          await b.look(yaw, pitch, true);
          return { ok: true, action: req.type, message: `looking yaw=${yaw.toFixed(2)} pitch=${pitch.toFixed(2)}` };
        }

        case 'jumpOnce':
          b.setControlState('jump', true);
          await delay(250);
          b.setControlState('jump', false);
          return { ok: true, action: req.type, message: 'jumped' };

        case 'move': {
          const dur = Math.min(Math.max(req.durationMs ?? 500, 50), 5000);
          const dir = req.direction ?? 'forward';
          if (req.sprint) b.setControlState('sprint', true);
          if (req.sneak) b.setControlState('sneak', true);
          if (req.jump) b.setControlState('jump', true);
          b.setControlState(dir, true);
          await delay(dur);
          b.setControlState(dir, false);
          if (req.jump) b.setControlState('jump', false);
          b.setControlState('sprint', false);
          b.setControlState('sneak', false);
          return { ok: true, action: req.type, message: `moved ${dir} for ${dur}ms` };
        }

        case 'lookAtNearest': {
          const target = this.nearest(req.entityType ?? 'any');
          if (!target) return { ok: false, action: req.type, message: 'no target found' };
          await b.lookAt(target.position.offset(0, target.height ?? 1.6, 0), true);
          return { ok: true, action: req.type, message: `looking at ${target.username ?? target.name}` };
        }

        case 'attackNearest': {
          const target = this.nearest(req.entityType ?? 'mob');
          if (!target) return { ok: false, action: req.type, message: 'no target found' };
          await b.lookAt(target.position.offset(0, target.height ?? 1.6, 0), true);
          b.attack(target);
          return { ok: true, action: req.type, message: `attacked ${target.username ?? target.name}` };
        }

        case 'useItem':
          b.activateItem();
          await delay(150);
          b.deactivateItem?.();
          return { ok: true, action: req.type, message: 'used held item' };

        default:
          return { ok: false, action: req.type, message: `unknown action: ${req.type}` };
      }
    } catch (err) {
      return { ok: false, action: req.type, message: err instanceof Error ? err.message : String(err) };
    }
  }

  private nearest(kind: 'player' | 'mob' | 'any'): any {
    const b = this.bot;
    return b.nearestEntity((e: any) => {
      if (kind === 'any') return true;
      if (kind === 'player') return e.type === 'player';
      return e.type === 'mob' || e.type === 'hostile' || e.type === 'animal';
    });
  }

  /** Directly set a control state (for live human keyboard/touch control). */
  setControl(key: 'forward' | 'back' | 'left' | 'right' | 'jump' | 'sprint' | 'sneak', state: boolean): void {
    if (!this._connected) return;
    this.bot.setControlState(key, state);
  }

  /** Nudge the view by a relative delta (mouse/touch look), radians. */
  lookDelta(dYaw: number, dPitch: number): void {
    if (!this._connected || !this.bot.entity) return;
    const yaw = this.bot.entity.yaw + dYaw;
    let pitch = this.bot.entity.pitch + dPitch;
    pitch = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, pitch));
    this.bot.look(yaw, pitch, false);
  }

  private clearControls(): void {
    for (const c of ['forward', 'back', 'left', 'right', 'jump', 'sprint', 'sneak'] as const) {
      this.bot.setControlState(c, false);
    }
  }

  disconnect(reason = 'session closed'): void {
    try {
      this.clearControls();
      this.bot?.quit?.(reason);
    } catch {
      /* ignore */
    }
    this._connected = false;
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}
