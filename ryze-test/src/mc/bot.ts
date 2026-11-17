import { EventEmitter } from 'node:events';
import { config } from '../config.js';
import { logger } from '../logger.js';
import { botAuthOptions } from '../auth/microsoft.js';
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
  get raw(): any {
    return this.bot;
  }

  async connect(): Promise<void> {
    const mineflayer = (await import('mineflayer')).default ?? (await import('mineflayer'));
    const auth = botAuthOptions();
    logger.info({ host: config.MC_HOST, port: config.MC_PORT, auth: auth.auth }, 'bot connecting');

    this.bot = (mineflayer as any).createBot({
      host: config.MC_HOST,
      port: config.MC_PORT,
      version: config.MC_VERSION,
      auth: auth.auth,
      username: auth.username,
      profilesFolder: auth.profilesFolder,
      hideErrors: false,
    });

    this.wireEvents();

    await new Promise<void>((resolve, reject) => {
      const onSpawn = () => {
        this._connected = true;
        cleanup();
        resolve();
      };
      const onError = (err: Error) => {
        this._error = err.message;
        cleanup();
        reject(err);
      };
      const onEnd = (reason: string) => {
        if (!this._connected) {
          cleanup();
          reject(new Error(`disconnected before spawn: ${reason}`));
        }
      };
      const cleanup = () => {
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
    this.bot.on('kicked', (reason: string) => {
      this._error = `kicked: ${typeof reason === 'string' ? reason : JSON.stringify(reason)}`;
      this._connected = false;
      this.emit('end', this._error);
    });
    this.bot.on('end', (reason: string) => {
      this._connected = false;
      this.emit('end', reason);
    });
    this.bot.on('error', (err: Error) => {
      this._error = err.message;
      this.emit('error', err);
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
